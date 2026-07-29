package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.net.Uri
import com.alcedo.studio.data.model.AiEmbedding
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.data.model.AiRating
import com.alcedo.studio.data.model.SemanticSearchResult
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [AiService]. Orchestrates the AI capabilities
 * (CLIP/SigLIP embedding, LLM culling/rating, image analysis, mask segmentation,
 * semantic tag generation) behind a single facade the ViewModels depend on.
 */
@Singleton
class AiServiceImpl @Inject constructor(
    private val clipEngine: ClipInferenceEngine,
    private val ratingService: AiRatingService,
    private val analysisService: ImageAnalysisService,
    private val maskService: MaskInferenceService,
    private val semanticService: SemanticGenerationService,
    private val sidecarRuntime: AiSidecarRuntimeService,
    private val credentialService: AiCredentialService,
) : AiService {

    override val isClipReady: Boolean
        get() = sidecarRuntime.state.value.loadedModelIds.isNotEmpty()

    override val isLlmReady: Boolean
        get() = credentialService.hasActiveCredentials()

    // ---- CLIP / semantic ----

    override suspend fun encodeImage(uri: Uri): AiEmbedding? = withContext(ThreadPool.aiInference) {
        val imageId = uri.toString()
        clipEngine.encodeImage(uri, imageId)
    }

    override suspend fun encodeText(text: String): FloatArray? = withContext(ThreadPool.aiInference) {
        clipEngine.encodeText(text)
    }

    override suspend fun semanticSearch(
        query: String,
        limit: Int,
    ): List<SemanticSearchResult> = withContext(ThreadPool.aiInference) {
        val queryVector = clipEngine.encodeText(query) ?: return@withContext emptyList()
        val asset = sidecarRuntime.defaultClipAsset()
        val results = clipEngine.search(queryVector, asset.id, limit)
        results.map { (imageId, score) ->
            SemanticSearchResult(
                imageId = imageId,
                score = score,
                modelId = asset.id,
            )
        }
    }

    // ---- LLM culling / analysis ----

    override suspend fun rateImage(uri: Uri, metadata: Map<String, String>): AiRating? =
        withContext(ThreadPool.aiInference) {
            val imageId = uri.toString()
            ratingService.rateImage(uri, imageId, metadata)
        }

    override suspend fun analyzeImage(uri: Uri): AiImageAnalysis? = withContext(ThreadPool.aiInference) {
        val imageId = uri.toString()
        analysisService.analyze(uri, imageId)
    }

    override suspend fun cullBatch(
        uris: List<Uri>,
        onProgress: (Int, Int) -> Unit,
    ): List<AiRating> = withContext(ThreadPool.aiInference) {
        val items = uris.map { uri -> uri to uri.toString() }
        val metadata = emptyMap<String, Map<String, String>>()
        ratingService.cullBatch(items, metadata, onProgress)
    }

    // ---- Mask segmentation ----

    override suspend fun segmentSubject(uri: Uri, kind: String): Bitmap? =
        withContext(ThreadPool.aiInference) {
            maskService.segment(uri, kind)
        }

    // ---- Semantic tag generation ----

    override suspend fun generateSemanticTags(uri: Uri): List<String> =
        withContext(ThreadPool.aiInference) {
            val imageId = uri.toString()
            semanticService.generateForUri(uri, imageId)
        }
}