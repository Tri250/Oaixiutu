package com.alcedo.studio.data.model

import androidx.room.*
import java.util.UUID

// --- Entities ---

@Entity(tableName = "sleeve_elements")
data class SleeveElementEntity(
    @PrimaryKey val elementId: Long,
    val elementName: String,
    val elementType: Int, // 1=file, 2=folder
    val parentId: Long,
    val addedTime: Long,
    val lastModifiedTime: Long,
    val syncFlag: Int = 0, // 0=unsync, 1=modified, 2=deleted, 3=synced
    val imageId: Long = 0,
    val currentVersionId: String = ""
)

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val dateAdded: Long,
    val dateModified: Long,
    val thumbnailPath: String = "",
    val isRaw: Boolean = false,
    val rawMake: String = "",
    val rawModel: String = "",
    val iso: Int = 0,
    val exposureTime: Double = 0.0,
    val fNumber: Double = 0.0,
    val focalLength: Double = 0.0,
    val whiteBalance: Int = 0,
    val rating: Int = 0,
    val colorLabel: Int = 0
)

@Entity(tableName = "edit_history")
data class EditHistoryEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val versionId: String,
    val parentId: String = "",
    val createdTime: Long,
    val name: String = "",
    val isActive: Boolean = true,
    val paramsJson: String = "{}"
)

@Entity(tableName = "pipeline_presets")
data class PipelinePresetEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val name: String,
    val category: String = "",
    val paramsJson: String,
    val createdTime: Long,
    val isBuiltIn: Boolean = false
)

@Entity(tableName = "ai_embeddings")
data class AiEmbeddingEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val embeddingData: ByteArray,  // serialized float array
    val modelVersion: String = "clip-vit-base-patch32",
    val createdTime: Long
)

// --- Utility functions ---

fun generateHash128(): String {
    return UUID.randomUUID().toString().replace("-", "")
}

// --- Pipeline parameters data class ---

data class PipelineParams(
    val exposure: Float = 0.0f,
    val contrast: Float = 0.0f,
    val highlights: Float = 0.0f,
    val shadows: Float = 0.0f,
    val midtones: Float = 0.0f,
    val whiteBalanceTemp: Float = 6500.0f,
    val whiteBalanceTint: Float = 0.0f,
    val saturation: Float = 0.0f,
    val vibrance: Float = 0.0f,
    val clarity: Float = 0.0f,
    val sharpen: Float = 0.0f,
    val toneCurvePoints: Int = 0,
    val filmGrain: Float = 0.0f,
    val vignette: Float = 0.0f
)

data class EditHistory(
    val boundImageId: Long,
    val versions: MutableList<HistoryVersion> = mutableListOf()
) {
    val defaultVersionId: String = "v0"
    var activeVersionId: String = defaultVersionId

    fun getDefaultVersion(): HistoryVersion? = versions.find { it.versionId == defaultVersionId }

    init {
        versions.add(HistoryVersion(defaultVersionId, System.currentTimeMillis()))
    }
}

data class HistoryVersion(
    val versionId: String,
    val createdTime: Long,
    val parentId: String = "",
    val name: String = "",
    val params: PipelineParams = PipelineParams()
)
