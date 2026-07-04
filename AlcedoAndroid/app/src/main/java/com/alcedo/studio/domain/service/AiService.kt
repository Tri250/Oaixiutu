package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiService(private val context: Context) {

    private val modelCatalog = mutableMapOf<String, AiModelProfile>()
    private val vectorIndex = mutableListOf<VectorIndexEntry>()

    init {
        // Register default model profiles
        modelCatalog["jina-clip-v2"] = AiModelProfile(
            modelId = "jina-clip-v2",
            modelName = "Jina CLIP v2",
            modelType = AiModelType.CLIP,
            description = "Lightweight multilingual CLIP model for image tagging and search"
        )
        modelCatalog["siglip2"] = AiModelProfile(
            modelId = "siglip2",
            modelName = "SigLIP 2",
            modelType = AiModelType.SIGLIP,
            description = "Google SigLIP 2 for zero-shot image classification"
        )
        modelCatalog["mobileclip2"] = AiModelProfile(
            modelId = "mobileclip2",
            modelName = "MobileCLIP S2",
            modelType = AiModelType.CLIP,
            description = "Apple MobileCLIP optimized for on-device inference"
        )
    }

    fun getModelCatalog(): List<AiModelProfile> = modelCatalog.values.toList()

    fun getActiveModel(): AiModelProfile? = modelCatalog.values.find { it.isActive }

    suspend fun activateModel(modelId: String) = withContext(Dispatchers.IO) {
        modelCatalog.values.forEach { }
        modelCatalog[modelId]?.let {
            modelCatalog[modelId] = it.copy(isActive = true)
        }
    }

    suspend fun generateLabels(imageId: UInt, bitmap: android.graphics.Bitmap?): List<SemanticLabel> = withContext(Dispatchers.Default) {
        // Placeholder for actual ONNX/TFLite inference
        // In production, load ONNX model via ONNX Runtime Mobile and run inference
        listOf(
            SemanticLabel("$imageId-1", imageId, "portrait", 0.92f, "mobileclip2"),
            SemanticLabel("$imageId-2", imageId, "outdoor", 0.87f, "mobileclip2"),
            SemanticLabel("$imageId-3", imageId, "natural light", 0.76f, "mobileclip2")
        )
    }

    suspend fun generateEmbedding(imageId: UInt, bitmap: android.graphics.Bitmap?): FloatArray = withContext(Dispatchers.Default) {
        // Placeholder: return random normalized vector
        FloatArray(512) { kotlin.random.Random.nextFloat() }.also {
            val norm = kotlin.math.sqrt(it.sumOf { v -> v * v.toDouble() }).toFloat()
            if (norm > 0) it.indices.forEach { i -> it[i] /= norm }
        }
    }

    suspend fun indexImage(imageId: UInt, embedding: FloatArray, modelId: String) = withContext(Dispatchers.IO) {
        vectorIndex.removeAll { it.imageId == imageId && it.modelId == modelId }
        vectorIndex.add(VectorIndexEntry(imageId, embedding, modelId))
    }

    suspend fun searchByText(query: String, topK: Int = 20): List<SearchResult> = withContext(Dispatchers.Default) {
        // Placeholder: convert query to embedding and do cosine similarity search
        val queryEmbedding = FloatArray(512) { kotlin.random.Random.nextFloat() }
        val norm = kotlin.math.sqrt(queryEmbedding.sumOf { v -> v * v.toDouble() }).toFloat()
        if (norm > 0) queryEmbedding.indices.forEach { i -> queryEmbedding[i] /= norm }

        vectorIndex.map { entry ->
            val dot = entry.embedding.zip(queryEmbedding).sumOf { (a, b) -> (a * b).toDouble() }.toFloat()
            SearchResult(entry.imageId, dot, ResultType.SEMANTIC)
        }.sortedByDescending { it.score }.take(topK)
    }

    suspend fun rateImage(imageId: UInt, bitmap: android.graphics.Bitmap?): Pair<Int, String> = withContext(Dispatchers.Default) {
        // Placeholder for VLM aesthetic rating
        val score = (3..5).random()
        val reason = when (score) {
            5 -> "Excellent composition and exposure."
            4 -> "Good image with minor improvements possible."
            else -> "Decent capture, could benefit from post-processing."
        }
        score to reason
    }
}
