package com.alcedo.studio.data.model

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// ================================================================
// Enums
// ================================================================

enum class ImageType {
    DEFAULT, JPEG, PNG, TIFF, ARW, CR2, CR3, NEF, DNG, HEIC, HEIF, WEBP, BMP, GIF, EXR,
    ORF, PEF, SRW, X3F, RAF, RW2, MOS
}

enum class ThumbState {
    NOT_PRESENT, PENDING, READY, FAILED
}

enum class ImageSyncState {
    SYNCED, UNSYNCED, MODIFIED, DELETED
}

enum class GpuBackendKind {
    NONE, CUDA, OPENCL, METAL, OPENGL_ES, VULKAN
}

// ================================================================
// Room Entity: Image Metadata
// ================================================================

@Entity(
    tableName = "image_metadata",
    indices = [
        Index(value = ["image_id"]),
        Index(value = ["camera_make"]),
        Index(value = ["camera_model"]),
        Index(value = ["lens_model"]),
        Index(value = ["focal_length"]),
        Index(value = ["aperture"]),
        Index(value = ["iso"]),
        Index(value = ["shutter_speed"]),
        Index(value = ["capture_date"]),
        Index(value = ["image_type"]),
        Index(value = ["rating"])
    ]
)
data class ImageMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "image_id")
    val imageId: Long,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "image_name")
    val imageName: String,

    @ColumnInfo(name = "image_type")
    val imageType: Int = ImageType.DEFAULT.ordinal,

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,

    @ColumnInfo(name = "width")
    val width: Int = 0,

    @ColumnInfo(name = "height")
    val height: Int = 0,

    @ColumnInfo(name = "checksum")
    val checksum: Long = 0L,

    @ColumnInfo(name = "mime_type")
    val mimeType: String = "",

    @ColumnInfo(name = "thumb_state")
    val thumbState: Int = ThumbState.NOT_PRESENT.ordinal,

    @ColumnInfo(name = "sync_state")
    val syncState: Int = ImageSyncState.SYNCED.ordinal,

    @ColumnInfo(name = "has_thumbnail")
    val hasThumbnail: Boolean = false,

    @ColumnInfo(name = "has_full_image")
    val hasFullImage: Boolean = false,

    @ColumnInfo(name = "has_exif")
    val hasExif: Boolean = false,

    @ColumnInfo(name = "has_exif_display")
    val hasExifDisplay: Boolean = false,

    @ColumnInfo(name = "has_raw_color_context")
    val hasRawColorContext: Boolean = false,

    @ColumnInfo(name = "thumb_pinned")
    val thumbPinned: Boolean = false,

    @ColumnInfo(name = "full_pinned")
    val fullPinned: Boolean = false,

    // EXIF fields
    @ColumnInfo(name = "camera_make")
    val cameraMake: String = "",

    @ColumnInfo(name = "camera_model")
    val cameraModel: String = "",

    @ColumnInfo(name = "lens_model")
    val lensModel: String = "",

    @ColumnInfo(name = "focal_length")
    val focalLength: Float = 0f,

    @ColumnInfo(name = "focal_length_35mm")
    val focalLength35mm: Float = 0f,

    @ColumnInfo(name = "aperture")
    val aperture: Float = 0f,

    @ColumnInfo(name = "shutter_speed")
    val shutterSpeed: Float = 0f,

    @ColumnInfo(name = "iso")
    val iso: Int = 0,

    @ColumnInfo(name = "capture_date")
    val captureDate: Long = 0L,

    @ColumnInfo(name = "image_size_display")
    val imageSizeDisplay: String = "",

    @ColumnInfo(name = "file_size_display")
    val fileSizeDisplay: String = "",

    @ColumnInfo(name = "exif_json")
    val exifJson: String = "",

    @ColumnInfo(name = "exif_display_json")
    val exifDisplayJson: String = "",

    @ColumnInfo(name = "raw_color_context_json")
    val rawColorContextJson: String = "",

    @ColumnInfo(name = "raw_cpid")
    val rawCpid: Int = 0,

    @ColumnInfo(name = "is_floating")
    val isFloating: Boolean = false,

    @ColumnInfo(name = "rating")
    val rating: Int = 0,

    @ColumnInfo(name = "imported_at")
    val importedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long = System.currentTimeMillis()
)

// ================================================================
// FTS Entity for Semantic Labels
// ================================================================

@Entity(tableName = "semantic_labels")
data class SemanticLabelEntity(
    @PrimaryKey
    @ColumnInfo(name = "label_id")
    val labelId: String,

    @ColumnInfo(name = "image_id")
    val imageId: Long,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float = 0f,

    @ColumnInfo(name = "model_id")
    val modelId: String = "",

    @ColumnInfo(name = "generated_at")
    val generatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vector_index")
data class VectorIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "image_id")
    val imageId: Long,

    @ColumnInfo(name = "embedding_blob")
    val embeddingBlob: ByteArray? = null,

    @ColumnInfo(name = "model_id")
    val modelId: String = ""
)

// ================================================================
// Display Models
// ================================================================

data class ExifDisplayMetaData(
    val cameraMake: String = "",
    val cameraModel: String = "",
    val lensModel: String = "",
    val focalLength: String = "",
    val aperture: String = "",
    val shutterSpeed: String = "",
    val iso: String = "",
    val captureDate: String = "",
    val imageSize: String = "",
    val fileSize: String = "",
    val rating: Int = 0,
    // 拍摄参数扩展
    val exposureCompensation: String = "",
    val maxAperture: String = "",
    val focalLength35mm: String = "",
    // 色彩
    val colorSpace: String = "",
    // GPS
    val gpsLatitude: String = "",
    val gpsLongitude: String = "",
    val gpsAltitude: String = "",
    // RAW 特有信息
    val bitsPerSample: Int = 0,
    val whiteBalanceMode: String = "",
    val demosaicAlgorithm: String = "",
    // 大疆特有信息
    val djiFlightHeight: String = "",
    val djiGpsMode: String = "",
    val djiGimbalPitch: String = "",
    // 华为/小米 AI 场景与多帧合成
    val aiScene: String = "",
    val multiFrameInfo: String = ""
)

data class RawRuntimeColorContext(
    val camRgbToSrgb: FloatArray = FloatArray(9) { 0f },
    val daylightMult: FloatArray = FloatArray(3) { 1f },
    val tungstenMult: FloatArray = FloatArray(3) { 1f },
    val cameraWhiteBalance: FloatArray = FloatArray(3) { 1f },
    val rawCpid: Int = 0,
    val isFloating: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawRuntimeColorContext) return false
        return camRgbToSrgb.contentEquals(other.camRgbToSrgb) &&
                daylightMult.contentEquals(other.daylightMult) &&
                tungstenMult.contentEquals(other.tungstenMult) &&
                cameraWhiteBalance.contentEquals(other.cameraWhiteBalance)
    }

    override fun hashCode(): Int {
        var result = camRgbToSrgb.contentHashCode()
        result = 31 * result + daylightMult.contentHashCode()
        result = 31 * result + tungstenMult.contentHashCode()
        result = 31 * result + cameraWhiteBalance.contentHashCode()
        return result
    }
}

data class ImageBuffer(
    var cpuData: Bitmap? = null,
    var gpuDataValid: Boolean = false,
    var buffer: ByteArray? = null,
    var bufferValid: Boolean = false,
    var gpuBackend: GpuBackendKind = GpuBackendKind.NONE
) {
    fun release() {
        cpuData?.recycle()
        cpuData = null
        buffer = null
        gpuDataValid = false
        bufferValid = false
    }

    fun clone(): ImageBuffer {
        val clonedCpu = cpuData?.let {
            if (!it.isRecycled) it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) else null
        }
        return copy(cpuData = clonedCpu)
    }
}

data class ImageModel(
    val imageId: Long,
    val imagePath: String,
    val imageName: String,
    val exifData: ExifInterface? = null,
    val exifDisplay: ExifDisplayMetaData = ExifDisplayMetaData(),
    val rawColorContext: RawRuntimeColorContext = RawRuntimeColorContext(),
    val hasRawColorContext: Boolean = false,
    val imageData: ImageBuffer = ImageBuffer(),
    val thumbnail: ImageBuffer = ImageBuffer(),
    val imageType: ImageType = ImageType.DEFAULT,
    val thumbState: ThumbState = ThumbState.NOT_PRESENT,
    val syncState: ImageSyncState = ImageSyncState.SYNCED,
    val checksum: Long = 0L,
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val hasFullImg: Boolean = false,
    val hasThumb: Boolean = false,
    val hasExif: Boolean = false,
    val hasExifDisplay: Boolean = false,
    val thumbPinned: Boolean = false,
    val fullPinned: Boolean = false,
    val rating: Int = 0,
    val captureTimestamp: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    fun clearData() {
        imageData.release()
    }

    fun clearThumbnail() {
        thumbnail.release()
    }

    fun toMetadataEntity(): ImageMetadataEntity = ImageMetadataEntity(
        imageId = imageId,
        imagePath = imagePath,
        imageName = imageName,
        imageType = imageType.ordinal,
        fileSize = fileSize,
        width = width,
        height = height,
        checksum = checksum,
        mimeType = mimeType,
        thumbState = thumbState.ordinal,
        syncState = syncState.ordinal,
        hasThumbnail = hasThumb,
        hasFullImage = hasFullImg,
        hasExif = hasExif,
        hasExifDisplay = hasExifDisplay,
        hasRawColorContext = hasRawColorContext,
        thumbPinned = thumbPinned,
        fullPinned = fullPinned,
        cameraMake = exifDisplay.cameraMake,
        cameraModel = exifDisplay.cameraModel,
        lensModel = exifDisplay.lensModel,
        focalLength = exifDisplay.focalLength.toFloatOrNull() ?: 0f,
        aperture = exifDisplay.aperture.toFloatOrNull() ?: 0f,
        shutterSpeed = parseShutterSpeed(exifDisplay.shutterSpeed),
        iso = exifDisplay.iso.toIntOrNull() ?: 0,
        captureDate = parseCaptureDate(exifDisplay.captureDate),
        imageSizeDisplay = exifDisplay.imageSize,
        fileSizeDisplay = exifDisplay.fileSize,
        rating = rating,
        importedAt = addedTimestamp
    )

    companion object {
        private val captureDateSdf by lazy {
            java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
        }

        private fun parseShutterSpeed(s: String): Float {
            if (s.isEmpty()) return 0f
            return try {
                if (s.contains("/")) {
                    val parts = s.split("/")
                    parts[0].toFloat() / parts[1].toFloat()
                } else {
                    s.toFloat()
                }
            } catch (_: Exception) {
                0f
            }
        }

        private fun parseCaptureDate(s: String): Long {
            if (s.isEmpty()) return 0L
            return try {
                synchronized(captureDateSdf) {
                    captureDateSdf.parse(s)?.time ?: 0L
                }
            } catch (_: Exception) {
                0L
            }
        }

        fun fromMetadataEntity(entity: ImageMetadataEntity): ImageModel {
            val safeImageType = try {
                if (entity.imageType in ImageType.entries.indices) ImageType.entries[entity.imageType] else ImageType.DEFAULT
            } catch (_: Exception) { ImageType.DEFAULT }

            val safeThumbState = try {
                if (entity.thumbState in ThumbState.entries.indices) ThumbState.entries[entity.thumbState] else ThumbState.NOT_PRESENT
            } catch (_: Exception) { ThumbState.NOT_PRESENT }

            val safeSyncState = try {
                if (entity.syncState in ImageSyncState.entries.indices) ImageSyncState.entries[entity.syncState] else ImageSyncState.SYNCED
            } catch (_: Exception) { ImageSyncState.SYNCED }

            return ImageModel(
                imageId = entity.imageId,
                imagePath = entity.imagePath,
                imageName = entity.imageName,
                exifDisplay = ExifDisplayMetaData(
                    cameraMake = entity.cameraMake,
                    cameraModel = entity.cameraModel,
                    lensModel = entity.lensModel,
                    focalLength = entity.focalLength.toString(),
                    aperture = entity.aperture.toString(),
                    shutterSpeed = entity.shutterSpeed.toString(),
                    iso = entity.iso.toString(),
                    captureDate = if (entity.captureDate > 0) {
                        synchronized(captureDateSdf) {
                            captureDateSdf.format(java.util.Date(entity.captureDate))
                        }
                    } else "",
                    imageSize = entity.imageSizeDisplay,
                    fileSize = entity.fileSizeDisplay,
                    rating = entity.rating
                ),
                imageType = safeImageType,
                thumbState = safeThumbState,
                syncState = safeSyncState,
                checksum = entity.checksum,
                fileSize = entity.fileSize,
                width = entity.width,
                height = entity.height,
                mimeType = entity.mimeType,
                hasFullImg = entity.hasFullImage,
                hasThumb = entity.hasThumbnail,
                hasExif = entity.hasExif,
                hasExifDisplay = entity.hasExifDisplay,
                hasRawColorContext = entity.hasRawColorContext,
                thumbPinned = entity.thumbPinned,
                fullPinned = entity.fullPinned,
                rating = entity.rating,
                captureTimestamp = entity.captureDate,
                addedTimestamp = entity.importedAt
            )
        }
    }
}

// ================================================================
// Exif Facet Models
// ================================================================

data class CameraFacet(
    val make: String,
    val model: String,
    val count: Int
)

data class LensFacet(
    val model: String,
    val count: Int
)

data class FocalLengthFacet(
    val range: String, // e.g. "24-35mm"
    val min: Float,
    val max: Float,
    val count: Int
)

data class ApertureFacet(
    val range: String, // e.g. "f/1.4-f/2.8"
    val min: Float,
    val max: Float,
    val count: Int
)

data class IsoFacet(
    val range: String, // e.g. "100-400"
    val min: Int,
    val max: Int,
    val count: Int
)

data class DateFacet(
    val year: Int,
    val month: Int?,
    val count: Int
)

data class RatingDistribution(
    val rating: Int,
    val count: Int
)

data class LabelFrequency(
    val label: String,
    val count: Int
)

// ================================================================
// Room Entity: Image (desktop schema)
// ================================================================

@Entity(
    tableName = "images",
    foreignKeys = [
        ForeignKey(
            entity = SleeveFileEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["format"]),
        Index(value = ["color_space"]),
        Index(value = ["is_hdr"]),
        Index(value = ["rating"]),
        Index(value = ["import_date"])
    ]
)
data class ImageEntity(
    @PrimaryKey
    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "width")
    val width: Int = 0,

    @ColumnInfo(name = "height")
    val height: Int = 0,

    @ColumnInfo(name = "format")
    val format: String = "",

    @ColumnInfo(name = "color_space")
    val colorSpace: String = "",

    @ColumnInfo(name = "bit_depth")
    val bitDepth: Int = 8,

    @ColumnInfo(name = "is_hdr")
    val isHdr: Boolean = false,

    @ColumnInfo(name = "rating")
    val rating: Int = 0,

    @ColumnInfo(name = "import_date")
    val importDate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_modified")
    val lastModified: Long = System.currentTimeMillis()
)

// ================================================================
// Room Entity: Pipeline (desktop schema)
// ================================================================

@Entity(
    tableName = "pipelines",
    foreignKeys = [
        ForeignKey(
            entity = SleeveFileEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["is_active"])
    ]
)
data class PipelineEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "pipeline_id")
    val pipelineId: Long = 0L,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "params_json")
    val paramsJson: String,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

// ================================================================
// Room Entity: History (desktop schema)
// ================================================================

@Entity(
    tableName = "histories",
    foreignKeys = [
        ForeignKey(
            entity = SleeveFileEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["version_id"]),
        Index(value = ["is_active"])
    ]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0L,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "version_id")
    val versionId: String,

    @ColumnInfo(name = "version_name")
    val versionName: String = "",

    @ColumnInfo(name = "params_json")
    val paramsJson: String,

    @ColumnInfo(name = "parent_version_id")
    val parentVersionId: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false
)

// ================================================================
// Room Entity: Filter (desktop schema)
// ================================================================

@Entity(
    tableName = "filters",
    indices = [
        Index(value = ["name"])
    ]
)
data class FilterEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "filter_id")
    val filterId: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "filter_json")
    val filterJson: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

// ================================================================
// Room Entity: AI Description (desktop schema)
// ================================================================

@Entity(
    tableName = "ai_descriptions",
    foreignKeys = [
        ForeignKey(
            entity = SleeveFileEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["task_id"]),
        Index(value = ["provider_id"]),
        Index(value = ["is_active"])
    ]
)
data class AiDescriptionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0L,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "task_id")
    val taskId: String = "",

    @ColumnInfo(name = "provider_id")
    val providerId: String = "",

    @ColumnInfo(name = "model_id")
    val modelId: String = "",

    @ColumnInfo(name = "caption")
    val caption: String = "",

    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",

    @ColumnInfo(name = "scene")
    val scene: String = "",

    @ColumnInfo(name = "confidence")
    val confidence: Float = 0f,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

// ================================================================
// Room Entity: AI Rating (desktop schema)
// ================================================================

@Entity(
    tableName = "ai_ratings",
    foreignKeys = [
        ForeignKey(
            entity = SleeveFileEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["task_id"]),
        Index(value = ["provider_id"]),
        Index(value = ["is_active"])
    ]
)
data class AiRatingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0L,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "task_id")
    val taskId: String = "",

    @ColumnInfo(name = "provider_id")
    val providerId: String = "",

    @ColumnInfo(name = "model_id")
    val modelId: String = "",

    @ColumnInfo(name = "rating")
    val rating: Int = 0,

    @ColumnInfo(name = "rubric_id")
    val rubricId: String = "",

    @ColumnInfo(name = "reasons_json")
    val reasonsJson: String = "[]",

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

// ================================================================
// Room Entity: Semantic Embedding (desktop schema)
// ================================================================

@Entity(
    tableName = "semantic_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = SleeveFileEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["model_id"])
    ]
)
data class SemanticEmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0L,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "model_id")
    val modelId: String = "",

    @ColumnInfo(name = "embedding_blob")
    val embeddingBlob: ByteArray = byteArrayOf(),

    @ColumnInfo(name = "dimension")
    val dimension: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SemanticEmbeddingEntity) return false
        return rowId == other.rowId && fileId == other.fileId && embeddingBlob.contentEquals(other.embeddingBlob)
    }

    override fun hashCode(): Int {
        var result = rowId.hashCode()
        result = 31 * result + fileId.hashCode()
        result = 31 * result + embeddingBlob.contentHashCode()
        return result
    }
}

// ================================================================
// Room Entity: Semantic Label (desktop schema - v2)
// ================================================================

@Entity(
    tableName = "semantic_labels_v2",
    foreignKeys = [
        ForeignKey(
            entity = SleeveFileEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["model_id"]),
        Index(value = ["primary_label"])
    ]
)
data class SemanticLabelV2Entity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0L,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "model_id")
    val modelId: String = "",

    @ColumnInfo(name = "primary_label")
    val primaryLabel: String = "",

    @ColumnInfo(name = "secondary_label")
    val secondaryLabel: String = "",

    @ColumnInfo(name = "primary_confidence")
    val primaryConfidence: Float = 0f,

    @ColumnInfo(name = "marginal")
    val marginal: Float = 0f,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

// ================================================================
// Room Entity: Collection (desktop schema - replaces existing)
// ================================================================

@Entity(
    tableName = "collections_v2",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class CollectionV2Entity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "collection_id")
    val collectionId: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "cover_file_id")
    val coverFileId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

// ================================================================
// Room Entity: Collection Image (desktop schema - v2)
// ================================================================

@Entity(
    tableName = "collection_images_v2",
    primaryKeys = ["collection_id", "file_id"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionV2Entity::class,
            parentColumns = ["collection_id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SleeveFileEntity::class,
            parentColumns = ["element_id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"])
    ]
)
data class CollectionImageV2Entity(
    @ColumnInfo(name = "collection_id")
    val collectionId: Long,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

enum class AiProviderType {
    OPENAI,
    ANTHROPIC,
    DOUBAO,
    CUSTOM
}

data class AiProviderProfile(
    val providerId: String,
    val providerName: String,
    val providerType: AiProviderType,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val maxTokens: Int = 4096
)

data class AiCredential(
    val providerId: String,
    val apiKey: String = "",
    val apiBaseUrl: String = "",
    val providerName: String = "",
    val isActive: Boolean = false,
    val defaultBaseUrl: String = "",
    val defaultModel: String = ""
)