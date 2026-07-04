package com.alcedo.studio.data.model

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.time.Instant

enum class ImageType {
    DEFAULT, JPEG, PNG, TIFF, ARW, CR2, CR3, NEF, DNG
}

enum class ThumbState {
    NOT_PRESENT, PENDING, READY, FAILED
}

enum class ImageSyncState {
    SYNCED, UNSYNCED, MODIFIED, DELETED
}

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

enum class GpuBackendKind {
    NONE, CUDA, OPENCL, METAL, OPENGL_ES, VULKAN
}

data class ImageModel(
    val imageId: UInt,
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
    val checksum: ULong = 0u,
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
}
