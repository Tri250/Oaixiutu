package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.AiModelType
import com.alcedo.studio.data.model.ModelAsset

class AiService(private val context: Context) {

    fun initialize() {}

    fun shutdown() {}

    fun isReady(): Boolean = false

    suspend fun loadModel(modelPath: String, modelType: AiModelType): Boolean = false

    suspend fun unloadModel() {}

    suspend fun activateModel(modelId: String): Boolean = false

    suspend fun deactivateModel(modelId: String) {}

    fun getActiveModel(): ModelAsset? = null

    suspend fun embedText(text: String): FloatArray = FloatArray(0)

    suspend fun embedImage(imagePath: String): FloatArray = FloatArray(0)

    suspend fun computeSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float = 0f

    suspend fun rateImage(imagePath: String, prompt: String): Map<String, Any> = emptyMap()

    fun getModelPath(modelId: String): String? = null
}
