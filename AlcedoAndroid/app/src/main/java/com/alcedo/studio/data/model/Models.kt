package com.alcedo.studio.data.model

import androidx.room.*
import java.util.UUID

// --- Entities ---

@Entity(
    tableName = "sleeve_elements",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["elementType"]),
        Index(value = ["imageId"]),
        Index(value = ["syncFlag"]),
        Index(value = ["elementName"])
    ]
)
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

@Entity(
    tableName = "images",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["dateAdded"]),
        Index(value = ["dateModified"]),
        Index(value = ["rating"]),
        Index(value = ["colorLabel"]),
        Index(value = ["isRaw"]),
        Index(value = ["rawMake"]),
        Index(value = ["rawModel"]),
        Index(value = ["iso"]),
        Index(value = ["focalLength"]),
        Index(value = ["fileName"])
    ]
)
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

@Entity(
    tableName = "edit_history",
    indices = [
        Index(value = ["imageId"]),
        Index(value = ["versionId"]),
        Index(value = ["parentId"])
    ]
)
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

@Entity(
    tableName = "pipeline_presets",
    indices = [
        Index(value = ["category"]),
        Index(value = ["name"])
    ]
)
data class PipelinePresetEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val name: String,
    val category: String = "",
    val paramsJson: String,
    val createdTime: Long,
    val isBuiltIn: Boolean = false
)

@Entity(
    tableName = "ai_embeddings",
    indices = [
        Index(value = ["imageId"]),
        Index(value = ["modelVersion"])
    ]
)
data class AiEmbeddingEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val embeddingData: ByteArray,  // serialized float array
    val modelVersion: String = "clip-vit-base-patch32",
    val createdTime: Long
)

@Entity(
    tableName = "sleeve_files",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["fileHash"]),
        Index(value = ["mimeType"])
    ]
)
data class FileEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val path: String,
    val size: Long,
    val mimeType: String,
    val fileHash: String = ""
)

@Entity(
    tableName = "sleeve_folders",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["folderName"])
    ]
)
data class FolderEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val parentId: Long,
    val folderName: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "image_metadata",
    indices = [
        Index(value = ["imageId"], unique = true),
        Index(value = ["cameraMake"]),
        Index(value = ["cameraModel"]),
        Index(value = ["lensModel"])
    ]
)
data class ImageMetadataEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val cameraMake: String = "",
    val cameraModel: String = "",
    val lensModel: String = "",
    val focalLength: Double = 0.0,
    val aperture: Double = 0.0,
    val exposureTime: Double = 0.0,
    val iso: Int = 0,
    val whiteBalance: Int = 0,
    val flashFired: Boolean = false,
    val gpsLatitude: Double = 0.0,
    val gpsLongitude: Double = 0.0,
    val gpsAltitude: Double = 0.0,
    val orientation: Int = 0,
    val dateTaken: Long = 0,
    val software: String = "",
    val exifJson: String = "{}"
)

@Entity(
    tableName = "ratings",
    indices = [
        Index(value = ["imageId"], unique = true),
        Index(value = ["rating"]),
        Index(value = ["ratingSource"])
    ]
)
data class RatingEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val rating: Int = 0,
    val ratingSource: String = "user"
)

@Entity(
    tableName = "semantic_labels",
    indices = [
        Index(value = ["imageId"]),
        Index(value = ["label"]),
        Index(value = ["source"]),
        Index(value = ["confidence"])
    ]
)
data class SemanticLabelEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val label: String,
    val confidence: Float = 0.0f,
    val source: String = "ai"
)

@Entity(
    tableName = "collections",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["sortOrder"])
    ]
)
data class CollectionEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val name: String,
    val coverImageId: Long = 0,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "filters",
    indices = [
        Index(value = ["category"]),
        Index(value = ["name"])
    ]
)
data class FilterEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val name: String,
    val category: String = "",
    val paramsJson: String = "{}"
)

@Entity(
    tableName = "pipeline_state",
    indices = [
        Index(value = ["imageId"], unique = true),
        Index(value = ["createdTime"])
    ]
)
data class PipelineEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val paramsJson: String = "{}",
    val createdTime: Long
)

@Entity(
    tableName = "filters_v2",
    indices = [
        Index(value = ["category"]),
        Index(value = ["name"]),
        Index(value = ["previewPath"])
    ]
)
data class FilterV2Entity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val name: String,
    val category: String = "",
    val paramsJson: String = "{}",
    val previewPath: String = ""
)

@Entity(
    tableName = "ai_descriptions",
    indices = [
        Index(value = ["imageId"], unique = true),
        Index(value = ["model"])
    ]
)
data class AiDescriptionEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val descriptionText: String = "",
    val model: String = ""
)

@Entity(
    tableName = "ai_ratings",
    indices = [
        Index(value = ["imageId"], unique = true),
        Index(value = ["qualityScore"]),
        Index(value = ["aestheticScore"])
    ]
)
data class AiRatingEntity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val qualityScore: Float = 0.0f,
    val aestheticScore: Float = 0.0f,
    val technicalScore: Float = 0.0f
)

@Entity(
    tableName = "semantic_labels_v2",
    indices = [
        Index(value = ["imageId"]),
        Index(value = ["label"]),
        Index(value = ["category"]),
        Index(value = ["source"]),
        Index(value = ["confidence"])
    ]
)
data class SemanticLabelV2Entity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val imageId: Long,
    val label: String,
    val confidence: Float = 0.0f,
    val category: String = "",
    val source: String = "ai"
)

@Entity(
    tableName = "collections_v2",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["isSmart"])
    ]
)
data class CollectionV2Entity(
    @PrimaryKey autoGenerate = true val id: Long = 0,
    val name: String,
    val query: String = "",
    val isSmart: Boolean = false,
    val coverImageId: Long = 0
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
