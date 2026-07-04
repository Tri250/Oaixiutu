package com.alcedo.studio.data.model

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// ================================================================
// Enums
// ================================================================

enum class ImageType {
    DEFAULT, JPEG, PNG, TIFF, ARW, CR2, CR3, NEF, DNG, HEIC, HEIF, WEBP, BMP, GIF, EXR
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
    val fileSize: String = ""
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

    fun clone(): ImageBuffer = copy(
        cpuData = cpuData?.config?.let { cpuData!!.copy(it, false) }
    )
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
    val fullPinned: Boolean = false
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
        fileSizeDisplay = exifDisplay.fileSize
    )

    companion object {
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
                val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                sdf.parse(s)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }

        fun fromMetadataEntity(entity: ImageMetadataEntity): ImageModel {
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
                        val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                        sdf.format(java.util.Date(entity.captureDate))
                    } else "",
                    imageSize = entity.imageSizeDisplay,
                    fileSize = entity.fileSizeDisplay
                ),
                imageType = ImageType.entries.getOrElse(entity.imageType) { ImageType.DEFAULT },
                thumbState = ThumbState.entries.getOrElse(entity.thumbState) { ThumbState.NOT_PRESENT },
                syncState = ImageSyncState.entries.getOrElse(entity.syncState) { ImageSyncState.SYNCED },
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
                fullPinned = entity.fullPinned
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