package com.alcedo.studio.domain.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encodes images for AI analysis (CLIP embedding generation).
 * Ported from desktop image_analysis_encoder.cpp
 * Handles image preprocessing (resize, normalize) before AI inference.
 */
class ImageAnalysisEncoder(private val clipEngine: ClipInferenceEngine) {

    data class AnalysisResult(
        val embedding: FloatArray,
        val confidence: Float,
        val processingTimeMs: Long
    )

    suspend fun encodeImage(imagePath: String): Result<AnalysisResult> = withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()
            val embedding = clipEngine.getImageEmbedding(imagePath)
            val elapsed = System.currentTimeMillis() - startTime
            Result.success(AnalysisResult(embedding, 1.0f, elapsed))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun encodeText(text: String): Result<FloatArray> = withContext(Dispatchers.Default) {
        try {
            val embedding = clipEngine.getTextEmbedding(text)
            Result.success(embedding)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val EMBEDDING_DIMENSION = 512
        const val INPUT_IMAGE_SIZE = 224
    }
}
