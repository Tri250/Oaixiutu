package com.alcedo.studio.domain.service

import android.net.Uri
import com.alcedo.studio.ai.LlmCullingClient
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LLM image analysis service. Wraps [LlmCullingClient] to produce a structured
 * [AiImageAnalysis] (caption, description, scene, subjects, colors, mood) for
 * a single image. Used by the AI screens and the semantic-generation flow.
 */
@Singleton
class ImageAnalysisService @Inject constructor(
    private val llmClient: LlmCullingClient,
    private val encoder: ImageAnalysisEncoder,
    private val credentialService: AiCredentialService,
) {

    /** Analyse [uri]; returns null when no LLM is configured or the call fails. */
    suspend fun analyze(uri: Uri, imageId: String): AiImageAnalysis? = withContext(ThreadPool.aiInference) {
        val profile = credentialService.activeProfile() ?: return@withContext null
        if (!credentialService.hasActiveCredentials()) return@withContext null
        if (!profile.supportsVision) return@withContext null
        val base64 = encoder.encodeThumbnail(uri, 768) ?: return@withContext null
        llmClient.injectedKey = credentialService.getApiKey(profile.id)
        llmClient.analyzeImage(imageId, base64, profile)
    }

    /** Batch-analyse a set of images, returning the non-null results. */
    suspend fun analyzeBatch(
        items: List<Pair<Uri, String>>,
        onProgress: (Int, Int) -> Unit,
    ): List<AiImageAnalysis> = withContext(ThreadPool.aiInference) {
        val results = mutableListOf<AiImageAnalysis>()
        items.forEachIndexed { index, (uri, imageId) ->
            val a = runCatching { analyze(uri, imageId) }.getOrNull()
            if (a != null) results.add(a)
            onProgress(index + 1, items.size)
        }
        results
    }
}
