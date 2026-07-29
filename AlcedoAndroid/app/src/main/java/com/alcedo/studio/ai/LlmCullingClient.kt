package com.alcedo.studio.ai

import android.util.Log
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.data.model.AiRating
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.domain.service.AiCredentialStore
import com.alcedo.studio.domain.service.AiProviderProfile
import com.alcedo.studio.security.SecureHttpClient
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM API client for culling and image analysis. Talks to OpenAI-compatible
 * (and Anthropic) vision endpoints via [SecureHttpClient], building a chat
 * completion with a base64 image and parsing the structured JSON response into
 * [AiRating] / [AiImageAnalysis]. Includes a robust prompt + fallback parser.
 *
 * API keys are resolved per-call from the encrypted [AiCredentialStore] (backed
 * by EncryptedSharedPreferences / Android Keystore). The key is never held in a
 * plaintext field, never written to logs, and only lives long enough to be
 * placed in the Authorization header of the outgoing request.
 */
@Singleton
class LlmCullingClient @Inject constructor(
    private val httpClient: SecureHttpClient,
    private val credentialStore: AiCredentialStore,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Rate a single image. [base64] is a raw base64 JPEG (no data URI prefix). */
    suspend fun rateImage(
        imageId: String,
        base64: String,
        metadata: Map<String, String>,
        profile: AiProviderProfile,
    ): AiRating? = withContext(ThreadPool.aiInference) {
        val apiKey = resolveKey(profile) ?: return@withContext null
        val system = RATE_SYSTEM_PROMPT
        val user = buildRateUserPrompt(base64, metadata)
        val raw = callChat(profile, apiKey, system, user, maxTokens = 600) ?: return@withContext null
        parseRating(imageId, raw, profile)
    }

    /** Analyse an image (caption + description + tags). */
    suspend fun analyzeImage(
        imageId: String,
        base64: String,
        profile: AiProviderProfile,
    ): AiImageAnalysis? = withContext(ThreadPool.aiInference) {
        val apiKey = resolveKey(profile) ?: return@withContext null
        val system = ANALYSIS_SYSTEM_PROMPT
        val user = buildAnalysisUserPrompt(base64)
        val raw = callChat(profile, apiKey, system, user, maxTokens = 800) ?: return@withContext null
        parseAnalysis(imageId, raw, profile)
    }

    /**
     * Resolve the API key for [profile] from the encrypted credential store.
     * Returns null (and logs a warning that excludes the key itself) when no
     * key is configured for the provider.
     */
    private fun resolveKey(profile: AiProviderProfile): String? {
        val key = credentialStore.getApiKey(profile.id)
        if (key == null) {
            Log.w(TAG, "no API key in credential store for provider ${profile.id}")
        }
        return key
    }

    private suspend fun callChat(
        profile: AiProviderProfile,
        apiKey: String,
        system: String,
        userPrompt: String,
        maxTokens: Int,
    ): String? = withContext(ThreadPool.io) {
        runCatching {
            val body = buildChatBody(profile, system, userPrompt, maxTokens)
            val request = Request.Builder()
                .url("${profile.baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.client.newCall(request).execute().use { resp ->
                // Never log the request (it carries the API key) or the
                // Authorization header; only surface the status code.
                if (!resp.isSuccessful) {
                    Log.w(TAG, "LLM HTTP ${resp.code} for ${profile.id}")
                    return@use null
                }
                val text = resp.body?.string() ?: return@use null
                extractContent(text)
            }
        }.onFailure { Log.w(TAG, "LLM call failed", it) }.getOrNull()
    }

    private fun buildChatBody(
        profile: AiProviderProfile,
        system: String,
        userPrompt: String,
        maxTokens: Int,
    ): String = buildFinalBody(profile, system, userPrompt, maxTokens)

    private fun buildFinalBody(
        profile: AiProviderProfile,
        system: String,
        userPrompt: String,
        maxTokens: Int,
    ): String {
        // userPrompt ends with "BASE64:<b64>"; split it out for the image part.
        val (textPart, imagePart) = if (userPrompt.contains("BASE64:")) {
            userPrompt.substringBefore("BASE64:") to userPrompt.substringAfter("BASE64:")
        } else {
            userPrompt to null
        }
        val obj = buildJsonObject {
            put("model", profile.defaultModel)
            put("max_tokens", maxTokens)
            put("temperature", 0.2)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject {
                    put("role", "user")
                    if (imagePart != null) {
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", textPart) })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$imagePart") })
                            })
                        })
                    } else {
                        put("content", textPart)
                    }
                })
            })
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun extractContent(responseJson: String): String? = runCatching {
        val obj = json.parseToJsonElement(responseJson).jsonObject
        val choices = obj["choices"]?.jsonArray ?: return null
        val first = choices.firstOrNull()?.jsonObject ?: return null
        val message = first["message"]?.jsonObject ?: return null
        message["content"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun buildRateUserPrompt(base64: String, metadata: Map<String, String>): String {
        val meta = metadata.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        return "Evaluate this photograph for culling. EXIF:\n$meta\n\n" +
            "Return STRICT JSON only with keys: overall, technical, aesthetic, sharpness, " +
            "exposure, composition, emotion (each 0..1 float), suggestedRating (1..5 int), " +
            "suggestedFlag (\"NONE\"|\"PICK\"|\"REJECT\"), rationale (short string).\n" +
            "BASE64:$base64"
    }

    private fun buildAnalysisUserPrompt(base64: String): String =
        "Describe this photograph. Return STRICT JSON with keys: caption (one sentence), " +
            "description (2-3 sentences), tags (array of <=12 short lowercase tags), " +
            "scene, subjects (array), dominantColors (array), mood, season, timeOfDay.\n" +
            "BASE64:$base64"

    private fun parseRating(imageId: String, raw: String, profile: AiProviderProfile): AiRating? {
        val obj = parseJsonObjectLoose(raw) ?: return null
        val now = System.currentTimeMillis()
        return AiRating(
            imageId = imageId,
            overallScore = obj.float("overall"),
            technicalScore = obj.float("technical"),
            aestheticScore = obj.float("aesthetic"),
            sharpnessScore = obj.float("sharpness"),
            exposureScore = obj.float("exposure"),
            compositionScore = obj.float("composition"),
            emotionScore = obj.float("emotion"),
            rationale = obj.string("rationale"),
            suggestedRating = obj.int("suggestedRating", 3),
            suggestedFlag = runCatching {
                ImageFlag.valueOf(obj.string("suggestedFlag").uppercase())
            }.getOrDefault(ImageFlag.NONE),
            generatedAt = now,
            modelId = profile.defaultModel,
            provider = profile.id,
            confidence = 0.8f,
        )
    }

    private fun parseAnalysis(imageId: String, raw: String, profile: AiProviderProfile): AiImageAnalysis? {
        val obj = parseJsonObjectLoose(raw) ?: return null
        return AiImageAnalysis(
            imageId = imageId,
            caption = obj.string("caption"),
            detailedDescription = obj.string("description"),
            tags = obj.array("tags"),
            sceneType = obj["scene"]?.jsonPrimitive?.contentOrNull,
            subjects = obj.array("subjects"),
            dominantColors = obj.array("dominantColors"),
            mood = obj["mood"]?.jsonPrimitive?.contentOrNull,
            season = obj["season"]?.jsonPrimitive?.contentOrNull,
            timeOfDay = obj["timeOfDay"]?.jsonPrimitive?.contentOrNull,
            generatedAt = System.currentTimeMillis(),
            modelId = profile.defaultModel,
            provider = profile.id,
        )
    }

    /** Parse a JSON object, tolerating leading/trailing prose around the braces. */
    private fun parseJsonObjectLoose(raw: String): JsonObject? = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
    }.onFailure { Log.w(TAG, "parse failed: $raw", it) }.getOrNull()

    // ---- helpers over JsonObject ----
    private fun JsonObject.float(key: String): Float =
        (this[key]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f).coerceIn(0f, 1f)

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: ""

    private fun JsonObject.int(key: String, default: Int): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.array(key: String): List<String> = runCatching {
        (this[key] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
    }.getOrDefault(emptyList())

    companion object {
        private const val TAG = "LlmCullingClient"
        private const val RATE_SYSTEM_PROMPT =
            "You are a professional photo editor assisting with culling. " +
                "Score the image objectively. Respond with JSON only."
        private const val ANALYSIS_SYSTEM_PROMPT =
            "You are an image captioning assistant for a photo editor. " +
                "Produce concise, accurate metadata. Respond with JSON only."
    }
}
