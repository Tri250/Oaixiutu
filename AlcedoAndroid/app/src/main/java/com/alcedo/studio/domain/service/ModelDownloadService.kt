package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.AiModelType
import com.alcedo.studio.data.model.ModelAsset
import com.alcedo.studio.data.model.ModelDownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ModelDownloadService(private val context: Context) {

    private val modelsDir = File(context.filesDir, "ai_models").also { it.mkdirs() }

    private val _models = MutableStateFlow<List<ModelAsset>>(emptyList())
    val models: StateFlow<List<ModelAsset>> = _models.asStateFlow()

    val defaultModels: List<ModelAsset> = emptyList()

    suspend fun refreshModelCatalog() {}

    fun getModelFile(modelId: String): File = File(modelsDir, "$modelId.onnx")

    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}): Boolean = false

    suspend fun cancelDownload(modelId: String) {}

    suspend fun activateModel(modelId: String): Boolean = false

    suspend fun deactivateModel(modelId: String) {}

    fun getActiveModelId(modelType: AiModelType): String? = null

    fun getActiveModel(): ModelAsset? = null

    fun getStorageUsage(): Long = 0L

    fun getAvailableStorage(): Long = context.filesDir.usableSpace

    suspend fun deleteModel(modelId: String) {}

    suspend fun clearAllModels() {}
}
