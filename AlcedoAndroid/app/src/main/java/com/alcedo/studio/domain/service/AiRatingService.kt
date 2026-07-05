package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * AI aesthetic rating service.
 *
 * Sends images to remote LLMs for aesthetic evaluation
 * including caption generation, tagging, and 1-5 star rating.
 * Supports OpenAI Vision, Anthropic Messages, and Doubao APIs.
 */
class AiRatingService(
    private val context: Context,
    private val credentialService: AiCredentialService
) {
    companion object {
        private const val TAG = "AiRatingService"
        private const val CONNECT_TIMEOUT_SEC = 30L
        private const val READ_TIMEOUT_SEC = 120L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val _ratingStatus = MutableStateFlow<Map<UInt, AiRatingStatus>>(emptyMap())
    val ratingStatus: StateFlow<Map<UInt, AiRatingStatus>> = _ratingStatus.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    private val activeJobs = mutableMapOf<UInt, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun moodPrompt(mood: RatingMood): String = when (mood) {
        RatingMood.PROFESSIONAL -> "You are a professional photography critic. Evaluate this image for technical excellence, composition, lighting, and artistic merit. Be thorough and precise."
        RatingMood.CASUAL -> "You are a friendly photography enthusiast. Rate this image based on overall appeal, beauty, and how much you enjoy it."
        RatingMood.SOCIAL_MEDIA -> "You are a social media content expert. Rate this image for engagement potential, visual impact, and shareability on platforms like Instagram."
        RatingMood.ARTISTIC -> "You are an art curator. Evaluate this image for artistic expression, emotional impact, creative vision, and aesthetic value."
        RatingMood.PHOTOJOURNALISM -> "You are a photojournalism editor. Rate this image for storytelling power, decisive moment, authenticity, and visual narrative."
        RatingMood.TECHNICAL -> "You are a technical photography analyst. Evaluate exposure accuracy, focus sharpness, noise levels, dynamic range, and color fidelity."
        RatingMood.MINIMALIST -> "You are a minimalist design critic. Rate this image for simplicity, negative space usage, clean composition, and visual clarity."
        RatingMood.VINTAGE -> "You are a vintage photography appraiser. Rate this image for nostalgic quality, tonal character, grain texture, and timeless appeal."
    }

    private fun ratingPrompt(mood: RatingMood): String {
        return "${moodPrompt(mood)}\n\nRespond in JSON format ONLY:\n{\"rating\": <1-5>, \"caption\": \"<short caption>\", \"tags\": [\"<tag1>\", \"<tag2>\"], \"reason\": \"<brief reasoning>\"}\n\nRating scale: 1=poor, 2=below average, 3=average, 4=good, 5=excellent."
    }

    suspend fun rateImage(
        imageId: UInt,
        bitmap: Bitmap,
        mood: RatingMood = RatingMood.CASUAL,
        providerId: String? = null
    ): AiRating? = withContext(Dispatchers.IO) {
        _ratingStatus.value = _ratingStatus.value.toMutableMap().apply {
            this[imageId] = AiRatingStatus.RUNNING
        }

        try {
            val credential = if (providerId != null) {
                credentialService.getCredential(providerId)
            } else {
                credentialService.getActiveCredential()
            }

            if (credential == null || credential.apiKey.isBlank()) {
                Log.e(TAG, "No active AI credential configured")
                _ratingStatus.value = _ratingStatus.value.toMutableMap().apply {
                    this[imageId] = AiRatingStatus.FAILED
                }
                return@withContext null
            }

            val base64Image = bitmapToBase64(bitmap)
            val prompt = ratingPrompt(mood)
            val profile = credentialService.defaultProfiles.find { it.providerId == credential.providerId }
            val providerType = profile?.providerType ?: AiProviderType.CUSTOM
            val baseUrl = credential.apiBaseUrl.ifEmpty { credential.defaultBaseUrl }
            val model = credential.defaultModel.ifEmpty { profile?.defaultModel ?: "" }

            val responseBody = when (providerType) {
                AiProviderType.OPENAI -> callOpenAi(baseUrl, credential.apiKey, model, prompt, base64Image)
                AiProviderType.ANTHROPIC -> callAnthropic(baseUrl, credential.apiKey, model, prompt, base64Image)
                AiProviderType.DOUBAO -> callDoubao(baseUrl, credential.apiKey, model, prompt, base64Image)
                AiProviderType.CUSTOM -> callOpenAi(baseUrl, credential.apiKey, model, prompt, base64Image)
            }

            if (responseBody == null) {
                Log.e(TAG, "LLM API call returned null for provider $providerType")
                _ratingStatus.value = _ratingStatus.value.toMutableMap().apply {
                    this[imageId] = AiRatingStatus.FAILED
                }
                return@withContext null
            }

            val rating = parseLlmResponse(responseBody, imageId, mood, credential.providerId)

            _ratingStatus.value = _ratingStatus.value.toMutableMap().apply {
                this[imageId] = AiRatingStatus.COMPLETED
            }

            rating
        } catch (e: CancellationException) {
            _ratingStatus.value = _ratingStatus.value.toMutableMap().apply {
                this[imageId] = AiRatingStatus.CANCELLED
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "rateImage failed: ${e.message}", e)
            _ratingStatus.value = _ratingStatus.value.toMutableMap().apply {
                this[imageId] = AiRatingStatus.FAILED
            }
            null
        }
    }

    suspend fun cancelRating(imageId: UInt) {
        activeJobs[imageId]?.cancel()
        activeJobs.remove(imageId)
        _ratingStatus.value = _ratingStatus.value.toMutableMap().apply {
            this[imageId] = AiRatingStatus.CANCELLED
        }
    }

    suspend fun rateImages(
        images: List<Pair<UInt, Bitmap>>,
        mood: RatingMood = RatingMood.CASUAL,
        providerId: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<AiRating> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AiRating>()
        for ((index, pair) in images.withIndex()) {
            ensureActive()
            val (imageId, bitmap) = pair
            val rating = rateImage(imageId, bitmap, mood, providerId)
            if (rating != null) {
                results.add(rating)
            }
            onProgress(index + 1, images.size)
        }
        results
    }

    suspend fun writeRatingToExif(imagePath: String, rating: AiRating): Boolean = withContext(Dispatchers.IO) {
        try {
            val exif = androidx.exifinterface.media.ExifInterface(imagePath)
            // EXIF Rating tag: 0x4746 (Microsoft Photo Rating)
            val ratingPercent = when (rating.stars) {
                1 -> 1
                2 -> 25
                3 -> 50
                4 -> 75
                5 -> 99
                else -> 0
            }
            exif.setAttribute("Rating", rating.stars.toString())
            exif.setAttribute("RatingPercent", ratingPercent.toString())
            if (rating.caption.isNotEmpty()) {
                exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION, rating.caption)
            }
            exif.saveAttributes()
            Log.i(TAG, "Wrote rating ${rating.stars} to EXIF for $imagePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeRatingToExif failed: ${e.message}", e)
            false
        }
    }

    // ── Bitmap to Base64 ──

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ── OpenAI Vision API ──

    private fun callOpenAi(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        base64Image: String
    ): String? {
        val jsonBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            }
                        })
                    }
                })
            }
        }

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        return executePost(url, apiKey, jsonBody)
    }

    // ── Anthropic Messages API ──

    private fun callAnthropic(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        base64Image: String
    ): String? {
        val jsonBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", base64Image)
                            }
                        })
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                    }
                })
            }
        }

        val url = baseUrl.trimEnd('/') + "/messages"
        return executePost(url, apiKey, jsonBody, extraHeaders = mapOf("anthropic-version" to "2023-06-01"))
    }

    // ── Doubao (Volcengine) API ──

    private fun callDoubao(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        base64Image: String
    ): String? {
        val jsonBody = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$base64Image")
                            }
                        })
                    }
                })
            }
        }

        val url = baseUrl.trimEnd('/') + "/chat/completions"
        return executePost(url, apiKey, jsonBody, authHeader = "Authorization")
    }

    // ── HTTP Execution ──

    private fun executePost(
        url: String,
        apiKey: String,
        jsonBody: String,
        authHeader: String = "Authorization",
        authPrefix: String = "Bearer ",
        extraHeaders: Map<String, String> = emptyMap()
    ): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader(authHeader, authPrefix + apiKey)
                .apply {
                    extraHeaders.forEach { (k, v) -> addHeader(k, v) }
                    if (authHeader != "Authorization") {
                        // For Doubao, also add Content-Type explicitly
                    }
                }
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "LLM API error: ${response.code} ${response.body?.string()?.take(500)}")
                return null
            }
            response.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "HTTP request failed: ${e.message}", e)
            null
        }
    }

    // ── Response Parsing ──

    private fun parseLlmResponse(
        responseBody: String,
        imageId: UInt,
        mood: RatingMood,
        providerId: String
    ): AiRating {
        try {
            val root = Json.parseToJsonElement(responseBody).jsonObject

            // Extract content text from response - supports both OpenAI and Anthropic formats
            val contentText = extractContentText(root)
            if (contentText.isNullOrBlank()) {
                Log.w(TAG, "No content text in LLM response")
                return defaultRating(imageId, mood, providerId)
            }

            // Try to parse JSON from the content (may be wrapped in markdown code blocks)
            val jsonStr = extractJsonFromContent(contentText)
            if (jsonStr != null) {
                val ratingObj = Json.parseToJsonElement(jsonStr).jsonObject
                val stars = ratingObj["rating"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 5) ?: 3
                val caption = ratingObj["caption"]?.jsonPrimitive?.contentOrNull ?: ""
                val tags = ratingObj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val reason = ratingObj["reason"]?.jsonPrimitive?.contentOrNull ?: ""

                return AiRating(
                    ratingId = UUID.randomUUID().toString(),
                    imageId = imageId,
                    stars = stars,
                    caption = caption,
                    tags = tags,
                    reason = reason,
                    mood = mood,
                    providerId = providerId
                )
            }

            // Fallback: try to extract rating from plain text
            val ratingRegex = Regex(""""?rating"?\s*[:=]\s*(\d)""")
            val ratingMatch = ratingRegex.find(contentText)
            val stars = ratingMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 5) ?: 3

            return AiRating(
                ratingId = UUID.randomUUID().toString(),
                imageId = imageId,
                stars = stars,
                caption = contentText.take(200),
                tags = emptyList(),
                reason = contentText.take(300),
                mood = mood,
                providerId = providerId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LLM response: ${e.message}", e)
            return defaultRating(imageId, mood, providerId)
        }
    }

    private fun extractContentText(root: JsonObject): String? {
        // OpenAI format: choices[0].message.content
        val choices = root["choices"]?.jsonArray
        if (choices != null && choices.isNotEmpty()) {
            val content = choices[0].jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            if (!content.isNullOrBlank()) return content
        }

        // Anthropic format: content[0].text
        val content = root["content"]?.jsonArray
        if (content != null && content.isNotEmpty()) {
            val text = content[0].jsonObject["text"]?.jsonPrimitive?.contentOrNull
            if (!text.isNullOrBlank()) return text
        }

        return null
    }

    private fun extractJsonFromContent(content: String): String? {
        // Try direct JSON parse
        try {
            Json.parseToJsonElement(content)
            return content
        } catch (_: Exception) {}

        // Try extracting from markdown code block
        val codeBlockRegex = Regex("""```(?:json)?\s*\n?(.*?)\n?```""", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockRegex.find(content)
        if (match != null) {
            val extracted = match.groupValues[1].trim()
            try {
                Json.parseToJsonElement(extracted)
                return extracted
            } catch (_: Exception) {}
        }

        // Try finding JSON object in text
        val jsonRegex = Regex("""\{[^{}]*"rating"[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        val jsonMatch = jsonRegex.find(content)
        if (jsonMatch != null) {
            try {
                Json.parseToJsonElement(jsonMatch.value)
                return jsonMatch.value
            } catch (_: Exception) {}
        }

        return null
    }

    private fun defaultRating(imageId: UInt, mood: RatingMood, providerId: String): AiRating {
        return AiRating(
            ratingId = UUID.randomUUID().toString(),
            imageId = imageId,
            stars = 3,
            caption = "",
            tags = emptyList(),
            reason = "Failed to parse LLM response",
            mood = mood,
            providerId = providerId
        )
    }

    // ── JSON Builder Helpers ──

    private fun buildJsonObject(builder: JsonObjectBuilder.() -> Unit): String {
        return JsonObject(builder).toString()
    }

    private fun JsonObjectBuilder.putJsonArray(key: String, builder: JsonArrayBuilder.() -> Unit) {
        put(key, JsonArray(builder))
    }

    private fun JsonObjectBuilder.putJsonObject(key: String, builder: JsonObjectBuilder.() -> Unit) {
        put(key, JsonObject(builder))
    }
}
