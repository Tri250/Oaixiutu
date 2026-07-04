package com.alcedo.studio.domain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Catalog of available AI models with metadata.
 * Ported from desktop model_asset_catalog.cpp
 */
data class ModelAsset(
    val id: String,
    val name: String,
    val type: ModelType,
    val version: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val checksumSha256: String,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
)

enum class ModelType {
    CLIP_IMAGE_ENCODER,
    CLIP_TEXT_ENCODER,
    IMAGE_ANALYSIS,
    SEMANTIC_SEARCH,
    AI_RATING
}

class ModelAssetCatalog {
    private val _models = MutableStateFlow<List<ModelAsset>>(emptyList())
    val models: StateFlow<List<ModelAsset>> = _models

    private val builtInModels = listOf(
        ModelAsset(
            id = "clip-vit-b-32-visual",
            name = "CLIP ViT-B/32 Visual",
            type = ModelType.CLIP_IMAGE_ENCODER,
            version = "1.0.0",
            sizeBytes = 350_000_000L,
            downloadUrl = "https://releases.alcedo.studio/models/clip-vit-b-32-visual.onnx",
            checksumSha256 = "placeholder_sha256"
        ),
        ModelAsset(
            id = "clip-vit-b-32-textual",
            name = "CLIP ViT-B/32 Textual",
            type = ModelType.CLIP_TEXT_ENCODER,
            version = "1.0.0",
            sizeBytes = 250_000_000L,
            downloadUrl = "https://releases.alcedo.studio/models/clip-vit-b-32-textual.onnx",
            checksumSha256 = "placeholder_sha256"
        ),
        ModelAsset(
            id = "alcedo-image-analysis",
            name = "Alcedo Image Analysis",
            type = ModelType.IMAGE_ANALYSIS,
            version = "1.0.0",
            sizeBytes = 150_000_000L,
            downloadUrl = "https://releases.alcedo.studio/models/alcedo-image-analysis.onnx",
            checksumSha256 = "placeholder_sha256"
        ),
        ModelAsset(
            id = "alcedo-ai-rating",
            name = "Alcedo AI Rating",
            type = ModelType.AI_RATING,
            version = "1.0.0",
            sizeBytes = 100_000_000L,
            downloadUrl = "https://releases.alcedo.studio/models/alcedo-ai-rating.onnx",
            checksumSha256 = "placeholder_sha256"
        )
    )

    fun initialize() {
        _models.value = builtInModels
    }

    fun getModel(id: String): ModelAsset? = _models.value.find { it.id == id }

    fun getModelsByType(type: ModelType): List<ModelAsset> =
        _models.value.filter { it.type == type }

    fun markDownloaded(modelId: String, localPath: String) {
        _models.value = _models.value.map {
            if (it.id == modelId) it.copy(isDownloaded = true, localPath = localPath) else it
        }
    }

    fun markRemoved(modelId: String) {
        _models.value = _models.value.map {
            if (it.id == modelId) it.copy(isDownloaded = false, localPath = null) else it
        }
    }

    fun getDownloadedModels(): List<ModelAsset> = _models.value.filter { it.isDownloaded }

    fun isModelReady(modelId: String): Boolean =
        _models.value.find { it.id == modelId }?.isDownloaded == true
}
