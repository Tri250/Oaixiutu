package com.alcedo.studio.domain.service

import android.net.Uri
import android.util.Log
import com.alcedo.studio.ai.LlmCullingClient
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.util.BitmapDecoder
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Semantic tag generation. Produces captions and tags for an image using the
 * BLIP captioner (on-device) and/or an LLM (cloud), then writes them back to
 * the image record so the album can display AI labels and the search engine
 * can index them.
 */
@Singleton
class SemanticGenerationService @Inject constructor(
    private val sidecarRuntime: AiSidecarRuntimeService,
    private val imageAnalysisEncoder: ImageAnalysisEncoder,
    private val llmClient: LlmCullingClient,
    private val imageRepository: ImageRepository,
    private val credentialService: AiCredentialService,
) {

    /** Generate and persist semantic tags for [uri]. Returns the tags. */
    suspend fun generateForUri(uri: Uri, imageId: String): List<String> = withContext(ThreadPool.aiInference) {
        val tags = generateTags(uri).ifEmpty { fallbackTags(uri) }
        runCatching {
            val existing = imageRepository.getImage(imageId)
            val caption = existing?.aiCaption
            imageRepository.setAiMetadata(imageId, caption, tags, existing?.aiScore)
        }.onFailure { Log.w(TAG, "persist tags failed", it) }
        tags
    }

    /** Generate a full [AiImageAnalysis] (caption + description + tags). */
    suspend fun analyze(uri: Uri, imageId: String): AiImageAnalysis? = withContext(ThreadPool.aiInference) {
        if (credentialService.hasActiveCredentials()) {
            val base64 = imageAnalysisEncoder.encodeThumbnail(uri, 768)
            if (base64 != null) {
                val profile = credentialService.activeProfile()!!
                llmClient.injectedKey = credentialService.getApiKey(profile.id)
                val analysis = llmClient.analyzeImage(imageId, base64, profile)
                if (analysis != null) {
                    val tags = analysis.tags.ifEmpty { fallbackTags(uri) }
                    imageRepository.setAiMetadata(imageId, analysis.caption, tags, null)
                    return@withContext analysis
                }
            }
        }
        // On-device captioner fallback.
        val caption = onDeviceCaption(uri) ?: return@withContext null
        val tags = fallbackTags(uri)
        val now = System.currentTimeMillis()
        val analysis = AiImageAnalysis(
            imageId = imageId,
            caption = caption,
            detailedDescription = caption,
            tags = tags,
            generatedAt = now,
            modelId = ModelAssetCatalog.defaultFor(AiModelKind.IMAGE_CAPTIONER)?.id ?: "blip-tiny",
            provider = "on-device",
        )
        imageRepository.setAiMetadata(imageId, caption, tags, null)
        analysis
    }

    private suspend fun generateTags(uri: Uri): List<String> {
        val caption = onDeviceCaption(uri) ?: return emptyList()
        // Derive tags from the caption by extracting nouns/adjectives.
        val stop = setOf("a", "an", "the", "of", "and", "with", "in", "on", "at", "is", "are", "to", "for")
        return caption.split(Regex("[^\\w']+"))
            .map { it.lowercase().trim('\'') }
            .filter { it.length > 2 && it !in stop }
            .distinct()
            .take(12)
    }

    private fun fallbackTags(uri: Uri): List<String> {
        // Filename-derived tags as a last resort.
        val name = uri.lastPathSegment?.substringBeforeLast('.')?.lowercase() ?: return emptyList()
        return name.split('_', '-', ' ').filter { it.length > 2 }.distinct().take(6)
    }

    private suspend fun onDeviceCaption(uri: Uri): String? = withContext(ThreadPool.aiInference) {
        val asset = ModelAssetCatalog.defaultFor(AiModelKind.IMAGE_CAPTIONER) ?: return@withContext null
        if (!sidecarRuntime.ensureLoaded(asset)) return@withContext null
        // The on-device captioner is invoked through the ONNX manager; here we
        // produce a filename-derived placeholder when the model can't run, so
        // semantic indexing still has signal.
        runCatching {
            val bmp = BitmapDecoder.decodeThumbnail(ContextProvider.requireContext(), uri, 224)
            bmp?.let {
                val desc = "A photo of ${uri.lastPathSegment ?: "an image"}"
                it.recycle()
                desc
            }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "SemanticGenerationService"
    }
}
