package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.data.model.AiRating
import com.alcedo.studio.data.model.AiEmbedding
import android.graphics.Bitmap
import android.net.Uri

/**
 * AI inference service contract. Aggregates the AI capabilities (CLIP embedding,
 * LLM rating/culling, image analysis, mask segmentation) behind a single facade
 * the ViewModels depend on. The default implementation orchestrates
 * [ClipInferenceEngine], [AiRatingService], [ImageAnalysisService] and the ONNX
 * runtime sidecar.
 */
interface AiService {

    /** Whether on-device CLIP/SigLIP models are loaded. */
    val isClipReady: Boolean

    /** Whether LLM culling credentials are configured. */
    val isLlmReady: Boolean

    // ---- CLIP / semantic ----
    suspend fun encodeImage(uri: Uri): AiEmbedding?
    suspend fun encodeText(text: String): FloatArray?
    suspend fun semanticSearch(query: String, limit: Int): List<com.alcedo.studio.data.model.SemanticSearchResult>

    // ---- LLM culling / analysis ----
    suspend fun rateImage(uri: Uri, metadata: Map<String, String>): AiRating?
    suspend fun analyzeImage(uri: Uri): AiImageAnalysis?
    suspend fun cullBatch(uris: List<Uri>, onProgress: (Int, Int) -> Unit): List<AiRating>

    // ---- Mask segmentation ----
    suspend fun segmentSubject(uri: Uri, kind: String): Bitmap?

    // ---- Semantic tag generation ----
    suspend fun generateSemanticTags(uri: Uri): List<String>
}
