package com.alcedo.studio.data.model

import kotlinx.serialization.Serializable

/**
 * AI rating / culling models. The LLM-based culling assist scores images for
 * technical quality (focus, exposure, composition) and aesthetic appeal, while
 * the CLIP/SigLIP embedding drives semantic search. This model is persisted in
 * Room and surfaced in the album inspector and AI screens.
 */
@Serializable
data class AiRating(
    val imageId: String,
    val overallScore: Float,
    val technicalScore: Float,
    val aestheticScore: Float,
    val sharpnessScore: Float,
    val exposureScore: Float,
    val compositionScore: Float,
    val emotionScore: Float,
    val rationale: String,
    val suggestedRating: Int,
    val suggestedFlag: ImageFlag,
    val generatedAt: Long,
    val modelId: String,
    val provider: String,
    val confidence: Float,
) {
    /** Map a 0..1 score to a 1..5 star recommendation. */
    fun stars(): Int = (overallScore * 5f).toInt().coerceIn(1, 5)
}

@Serializable
data class AiImageAnalysis(
    val imageId: String,
    val caption: String,
    val detailedDescription: String,
    val tags: List<String>,
    val sceneType: String? = null,
    val subjects: List<String> = emptyList(),
    val dominantColors: List<String> = emptyList(),
    val mood: String? = null,
    val season: String? = null,
    val timeOfDay: String? = null,
    val generatedAt: Long,
    val modelId: String,
    val provider: String,
)

@Serializable
data class AiEmbedding(
    val imageId: String,
    val embedding: FloatArray,
    val modelId: String,
    val dimensions: Int,
    val generatedAt: Long,
    val norm: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiEmbedding) return false
        return imageId == other.imageId && modelId == other.modelId &&
            dimensions == other.dimensions && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = imageId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + modelId.hashCode()
        return result
    }

    companion object {
        /** Cosine similarity in [-1, 1] for two L2-normalised vectors. */
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            require(a.size == b.size) { "vector length mismatch ${a.size} vs ${b.size}" }
            if (a.isEmpty()) return 0f
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
            return if (denom == 0.0) 0f else (dot / denom).toFloat()
        }
    }
}

@Serializable
data class SemanticSearchResult(
    val imageId: String,
    val score: Float,
    val matchedTags: List<String> = emptyList(),
    val matchedCaption: Boolean = false,
)

/** A model that describes an AI asset (CLIP, SigLIP, mask, etc.). */
@Serializable
data class AiModelAsset(
    val id: String,
    val name: String,
    val kind: AiModelKind,
    val version: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val sha256: String,
    val localPath: String?,
    val isDownloaded: Boolean,
    val isDefault: Boolean,
    val dimensions: Int = 0,
    val description: String = "",
)

@Serializable
enum class AiModelKind { CLIP, SIGLIP, MASK_SEGMENT, LLM_PROXY, IMAGE_CAPTIONER }
