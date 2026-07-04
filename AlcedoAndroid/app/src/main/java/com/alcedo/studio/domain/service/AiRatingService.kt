package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * AI aesthetic rating service.
 *
 * Sends images to remote LLMs (OpenAI, Anthropic, Doubao) for aesthetic evaluation
 * including caption generation, tagging, and 1-5 star rating. Supports multiple mood
 * levels. Writes ratings back to EXIF metadata.
 */
class AiRatingService(
    private val context: Context,
    private val credentialService: AiCredentialService
) {
    companion object {
        private const val TAG = "AiRatingService"
        private const val CONNECT_TIMEOUT_SEC = 30L
        private const val READ_TIMEOUT_SEC = 120L
        private const val MAX_IMAGE_DIMENSION = 1024
        private const val JPEG_QUALITY = 85
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ratingJobs = mutableMapOf<String, Job>()

    private val _ratingStatus = MutableStateFlow<Map<UInt, AiRatingStatus>>(emptyMap())
    val ratingStatus: StateFlow<Map<UInt, AiRatingStatus>> = _ratingStatus.asStateFlow()

    // ── Mood Prompts ──

    private fun getMoodPrompt(mood: RatingMood): String {
        return when (mood) {
            RatingMood.LITE -> """
You are a casual photography reviewer. Give a brief, friendly evaluation.
Rate the image 1-5 stars. Provide a short caption (1 sentence), 3-5 relevant tags, and a brief reason for the rating.
Respond in Chinese.
""".trimIndent()
            RatingMood.NORMAL -> """
You are an experienced photography critic. Evaluate the image comprehensively.
Consider composition, lighting, color, subject, technical quality, and emotional impact.
Rate the image 1-5 stars. Provide a descriptive caption, 5-8 tags, and a detailed reason.
Respond in Chinese.
""".trimIndent()
            RatingMood.HIGH -> """
You are a professional photography master with decades of experience. Scrutinize this image with a critical eye.
Analyze: composition (rule of thirds, leading lines, framing), lighting (exposure, dynamic range, shadows/highlights),
color (harmony, white balance, saturation), technical quality (sharpness, noise, focus),
subject matter (interest, storytelling, emotion), and overall aesthetic merit.
Rate 1-5 stars. Give a compelling caption, at least 8 precise tags, and a thorough critique.
Respond in Chinese. Be honest - if the image is mediocre, say so.
""".trimIndent()
            RatingMood.XHIGH -> """
你是一位老法师级别的摄影评论家，阅片无数，眼光极其毒辣。请用最挑剔的眼光审视这张照片。
你需要从以下维度进行严格评判：
1. 构图：三分法、引导线、框架、平衡感、负空间运用
2. 光影：曝光准确性、动态范围、高光/阴影细节、光质
3. 色彩：色彩和谐度、白平衡、饱和度控制、色调情绪
4. 技术：锐度、噪点、对焦精度、景深控制
5. 内容：主题吸引力、故事性、情感传达、瞬间捕捉
6. 审美：整体美感、艺术价值、创新性

评分1-5星。给出一个引人入胜的标题，至少10个精确标签，一篇详尽的评论。
请不要手下留情，实事求是地评价。
""".trimIndent()
            RatingMood.MAX -> """
你是一个终极摄影懂哥，你见过所有的好照片和烂照片，你拥有一双火眼金睛。你说话毫不留情，但你句句在理。
请你用最犀利的语言评价这张照片，好的地方使劲夸，烂的地方往死里骂。

从以下维度进行终极评判：
1. 构图：三分法、引导线、框架、平衡感、负空间、视角选择
2. 光影：曝光、动态范围、高光溢出/死黑、光质柔硬、光比
3. 色彩：色彩和谐度、白平衡、饱和度、色调情绪、色彩对比
4. 技术：锐度、噪点控制、对焦精度、景深、镜头素质体现
5. 内容：主题吸引力、故事性、情感传达、瞬间决定性、人文深度
6. 审美：整体美感、艺术价值、创新性、个人风格
7. 短板：这张照片最大的问题是什么？为什么它不够好？

评分1-5星。给出一个精准的标题，至少15个标签，一篇不留情面的深度评论。
请用中文回答，让你的评论成为一篇值得收藏的摄影教材。
""".trimIndent()
        }
    }

    // ── Rating ──

    suspend fun rateImage(
        imageId: UInt,
        bitmap: Bitmap,
        mood: RatingMood = RatingMood.NORMAL,
        providerId: String? = null
    ): AiRating? {
        val credential = providerId?.let { credentialService.getCredential(it) }
            ?: credentialService.getActiveCredential()
            ?: return null

        val ratingId = UUID.randomUUID().toString()
        setRatingStatus(imageId, AiRatingStatus.PENDING)

        // Cancel any existing rating for this image
        ratingJobs[imageId.toString()]?.cancel()

        val job = scope.launch {
            try {
                setRatingStatus(imageId, AiRatingStatus.RUNNING)

                val imageBase64 = compressAndEncodeImage(bitmap)
                val prompt = getMoodPrompt(mood)

                val response = callLLM(
                    credential = credential,
                    imageBase64 = imageBase64,
                    prompt = prompt,
                    mood = mood
                )

                if (response == null || !isActive) {
                    setRatingStatus(imageId, AiRatingStatus.FAILED)
                    return@launch
                }

                val parsed = parseRatingResponse(response)
                val rating = AiRating(
                    ratingId = ratingId,
                    imageId = imageId,
                    stars = parsed.stars.coerceIn(1, 5),
                    caption = parsed.caption,
                    tags = parsed.tags,
                    reason = parsed.reason,
                    mood = mood,
                    providerId = credential.providerId,
                    isWrittenToExif = false
                )

                setRatingStatus(imageId, AiRatingStatus.COMPLETED)
                return@launch rating
            } catch (e: CancellationException) {
                setRatingStatus(imageId, AiRatingStatus.CANCELLED)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Rating failed for image $imageId: ${e.message}")
                setRatingStatus(imageId, AiRatingStatus.FAILED)
                return@launch null
            }
        }

        ratingJobs[imageId.toString()] = job
        return job.await()
    }

    suspend fun cancelRating(imageId: UInt) {
        ratingJobs[imageId.toString()]?.cancel()
        ratingJobs.remove(imageId.toString())
    }

    // ── Batch Rating ──

    suspend fun rateImages(
        images: List<Pair<UInt, Bitmap>>,
        mood: RatingMood = RatingMood.NORMAL,
        providerId: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<AiRating> {
        val results = mutableListOf<AiRating>()
        for ((index, image) in images.withIndex()) {
            val rating = rateImage(image.first, image.second, mood, providerId)
            if (rating != null) {
                results.add(rating)
            }
            onProgress(index + 1, images.size)
        }
        return results
    }

    // ── EXIF Writing ──

    suspend fun writeRatingToExif(imagePath: String, rating: AiRating): Boolean = withContext(Dispatchers.IO) {
        try {
            val exif = ExifInterface(imagePath)

            // Write rating to EXIF UserComment
            val ratingJson = json.encodeToString(AiRating.serializer(), rating)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, ratingJson)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, rating.caption)
            exif.setAttribute(ExifInterface.TAG_XP_KEYWORDS, rating.tags.joinToString(";"))

            // Rating in EXIF Rating field (1-5)
            exif.setAttribute(ExifInterface.TAG_RATING, rating.stars.toString())

            exif.saveAttributes()
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write EXIF: ${e.message}")
            return@withContext false
        }
    }

    // ── LLM Calling ──

    private suspend fun callLLM(
        credential: AiCredential,
        imageBase64: String,
        prompt: String,
        mood: RatingMood
    ): String? {
        return when (credential.providerId) {
            "openai" -> callOpenAI(credential, imageBase64, prompt)
            "anthropic" -> callAnthropic(credential, imageBase64, prompt)
            "doubao" -> callDoubao(credential, imageBase64, prompt)
            else -> callOpenAI(credential, imageBase64, prompt) // Default to OpenAI-compatible
        }
    }

    private suspend fun callOpenAI(credential: AiCredential, imageBase64: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val baseUrl = credential.apiBaseUrl.ifEmpty { "https://api.openai.com/v1" }
        val url = "$baseUrl/chat/completions"

        val requestBody = buildJsonObject {
            put("model", credential.modelName.ifEmpty { "gpt-4o" })
            put("max_tokens", 2048)
            put("temperature", 0.7)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$imageBase64")
                                put("detail", "high")
                            }
                        }
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${credential.apiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Log.e(TAG, "OpenAI error: ${response.code}")
            // Never log the full response body as it may contain the key
            return null
        }

        return try {
            val jsonObj = json.parseToJsonElement(body).jsonObject
            jsonObj["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse OpenAI response")
            null
        }
    }

    private suspend fun callAnthropic(credential: AiCredential, imageBase64: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val baseUrl = credential.apiBaseUrl.ifEmpty { "https://api.anthropic.com/v1" }
        val url = "$baseUrl/messages"

        val requestBody = buildJsonObject {
            put("model", credential.modelName.ifEmpty { "claude-3-5-sonnet-20241022" })
            put("max_tokens", 2048)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", imageBase64)
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", credential.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Log.e(TAG, "Anthropic error: ${response.code}")
            return null
        }

        return try {
            val jsonObj = json.parseToJsonElement(body).jsonObject
            jsonObj["content"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Anthropic response")
            null
        }
    }

    private suspend fun callDoubao(credential: AiCredential, imageBase64: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val baseUrl = credential.apiBaseUrl.ifEmpty { "https://ark.cn-beijing.volces.com/api/v3" }
        val url = "$baseUrl/chat/completions"

        val requestBody = buildJsonObject {
            put("model", credential.modelName.ifEmpty { "doubao-vision-pro-32k" })
            put("max_tokens", 2048)
            put("temperature", 0.7)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$imageBase64")
                            }
                        }
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${credential.apiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return null

        if (!response.isSuccessful) {
            Log.e(TAG, "Doubao error: ${response.code}")
            return null
        }

        return try {
            val jsonObj = json.parseToJsonElement(body).jsonObject
            jsonObj["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Doubao response")
            null
        }
    }

    // ── Response Parsing ──

    data class ParsedRating(
        val stars: Int = 3,
        val caption: String = "",
        val tags: List<String> = emptyList(),
        val reason: String = ""
    )

    private fun parseRatingResponse(response: String): ParsedRating {
        var stars = 3
        var caption = ""
        val tags = mutableListOf<String>()
        val reason = response // Default: full response is the reason

        // Try to extract star rating
        val starPatterns = listOf(
            Regex("""(\d+)\s*[星⭐]"""),
            Regex("""评分[：:]\s*(\d+)"""),
            Regex("""(\d+)\s*\/\s*5"""),
            Regex("""rating[：:]\s*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*stars""", RegexOption.IGNORE_CASE),
        )

        for (pattern in starPatterns) {
            val match = pattern.find(response)
            if (match != null) {
                val s = match.groupValues[1].toIntOrNull()
                if (s != null && s in 1..5) {
                    stars = s
                    break
                }
            }
        }

        // Try to extract caption
        val captionPatterns = listOf(
            Regex("""标题[：:]\s*(.+?)(?:\n|$)"""),
            Regex("""caption[：:]\s*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE),
            Regex("""^#\s*(.+?)(?:\n|$)"""),
        )
        for (pattern in captionPatterns) {
            val match = pattern.find(response)
            if (match != null) {
                caption = match.groupValues[1].trim()
                break
            }
        }

        // Try to extract tags
        val tagPatterns = listOf(
            Regex("""标签[：:]\s*(.+?)(?:\n|$)"""),
            Regex("""tags[：:]\s*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE),
            Regex("""#[^\s#]+"""),
        )
        for (pattern in tagPatterns) {
            val matches = pattern.findAll(response).toList()
            if (matches.isNotEmpty()) {
                for (m in matches) {
                    val tagText = m.groupValues.getOrElse(1) { m.value }
                    tagText.split(Regex("""[,，、\s]+"""))
                        .map { it.trim().removePrefix("#").trim() }
                        .filter { it.isNotEmpty() && it.length < 30 }
                        .forEach { tags.add(it) }
                }
                if (tags.isNotEmpty()) break
            }
        }

        return ParsedRating(stars, caption, tags.distinct(), reason)
    }

    // ── Image Encoding ──

    private fun compressAndEncodeImage(bitmap: Bitmap): String {
        // Resize if too large
        val resized = if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
            val ratio = minOf(
                MAX_IMAGE_DIMENSION.toFloat() / bitmap.width,
                MAX_IMAGE_DIMENSION.toFloat() / bitmap.height
            )
            val newW = (bitmap.width * ratio).toInt()
            val newH = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else bitmap

        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val bytes = outputStream.toByteArray()

        if (resized !== bitmap) resized.recycle()

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun setRatingStatus(imageId: UInt, status: AiRatingStatus) {
        val current = _ratingStatus.value.toMutableMap()
        current[imageId] = status
        _ratingStatus.value = current
    }
}