package com.alcedo.studio.domain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Catalog of available AI models with metadata.
 * Ported from desktop model_asset_catalog.cpp
 */
data class CatalogModelAsset(
    val id: String,
    val name: String,
    val type: ModelType,
    val version: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    // null means checksum verification is skipped (with a warning); a concrete
    // hex digest enables strict integrity verification on download.
    val checksumSha256: String? = null,
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
    private val _models = MutableStateFlow<List<CatalogModelAsset>>(emptyList())
    val models: StateFlow<List<CatalogModelAsset>> = _models

    private val builtInModels = listOf(
        CatalogModelAsset(
            id = "clip-vit-b-32-visual",
            name = "CLIP ViT-B/32 Visual",
            type = ModelType.CLIP_IMAGE_ENCODER,
            version = "1.0.0",
            sizeBytes = 350_000_000L,
            downloadUrl = "https://releases.alcedo.studio/models/clip-vit-b-32-visual.onnx",
            checksumSha256 = null
        ),
        CatalogModelAsset(
            id = "clip-vit-b-32-textual",
            name = "CLIP ViT-B/32 Textual",
            type = ModelType.CLIP_TEXT_ENCODER,
            version = "1.0.0",
            sizeBytes = 250_000_000L,
            downloadUrl = "https://releases.alcedo.studio/models/clip-vit-b-32-textual.onnx",
            checksumSha256 = null
        ),
        CatalogModelAsset(
            id = "alcedo-image-analysis",
            name = "Alcedo Image Analysis",
            type = ModelType.IMAGE_ANALYSIS,
            version = "1.0.0",
            sizeBytes = 150_000_000L,
            downloadUrl = "https://releases.alcedo.studio/models/alcedo-image-analysis.onnx",
            checksumSha256 = null
        ),
        CatalogModelAsset(
            id = "alcedo-ai-rating",
            name = "Alcedo AI Rating",
            type = ModelType.AI_RATING,
            version = "1.0.0",
            sizeBytes = 100_000_000L,
            downloadUrl = "https://releases.alcedo.studio/models/alcedo-ai-rating.onnx",
            checksumSha256 = null
        )
    )

    fun initialize() {
        _models.value = builtInModels
    }

    fun getModel(id: String): CatalogModelAsset? = _models.value.find { it.id == id }

    fun getModelsByType(type: ModelType): List<CatalogModelAsset> =
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

    fun getDownloadedModels(): List<CatalogModelAsset> = _models.value.filter { it.isDownloaded }

    fun isModelReady(modelId: String): Boolean =
        _models.value.find { it.id == modelId }?.isDownloaded == true

    /**
     * 校验已下载模型文件的完整性。
     *
     * @param model      目标模型资产
     * @param actualHash 已下载文件的 SHA-256 十六进制摘要（小写）
     * @return 当 [CatalogModelAsset.checksumSha256] 为 null 时跳过校验并打印警告后返回 true；
     *         否则返回实际哈希与预期哈希是否一致。
     */
    fun verifyModelChecksum(model: CatalogModelAsset, actualHash: String): Boolean {
        val expected = model.checksumSha256
        if (expected == null) {
            android.util.Log.w(
                "ModelAssetCatalog",
                "校验跳过：模型 ${model.id} 未配置 checksumSha256，无法验证下载完整性"
            )
            return true
        }
        return actualHash.equals(expected, ignoreCase = true).also { matched ->
            if (!matched) {
                android.util.Log.e(
                    "ModelAssetCatalog",
                    "校验失败：模型 ${model.id} 期望哈希=$expected 实际哈希=$actualHash"
                )
            }
        }
    }
}