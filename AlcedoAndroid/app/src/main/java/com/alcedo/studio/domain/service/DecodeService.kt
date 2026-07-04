package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class DecodeService {

    data class DecodedRaw(
        val rgbData: FloatArray,
        val width: Int,
        val height: Int,
        val rawInfo: RawImageInfo,
        val thumbnailData: ByteArray?,
        val previewData: ByteArray?,
        val metadata: DecodedMetadata?
    )

    data class DecodedMetadata(
        val cameraMake: String = "",
        val cameraModel: String = "",
        val lensModel: String = "",
        val focalLength: Float = 0f,
        val aperture: Float = 0f,
        val shutterSpeed: Float = 0f,
        val iso: Float = 0f,
        val exposureBias: Float = 0f,
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        val orientation: Int = 1,
        val dateTime: String = "",
        val colorSpace: String = "",
        val hasGps: Boolean = false,
        val gpsLatitude: Double = 0.0,
        val gpsLongitude: Double = 0.0,
        val gpsAltitude: Double = 0.0,
        val rawJson: String = ""
    )

    data class RawImageInfo(
        val format: String = "",
        val make: String = "",
        val model: String = "",
        val rawWidth: Int = 0,
        val rawHeight: Int = 0,
        val bayerPattern: Int = 0,
        val whiteLevel: Int = 65535,
        val blackLevel: Int = 0
    )

    data class DecodeOptions(
        val demosaic: DemosaicMethod = DemosaicMethod.RCD,
        val highlightReconstruction: Boolean = true,
        val useCameraMatrix: Boolean = true,
        val halfResolution: Boolean = false,
        val outputFloat: Boolean = true,
        val extractThumbnail: Boolean = true,
        val extractPreview: Boolean = false,
        val maxThumbnailDimension: Int = 512,
        val whiteBalanceIlluminant: WBIlluminant = WBIlluminant.CAMERA_AUTO
    )

    enum class DemosaicMethod(val nativeValue: Int) {
        RCD(0), AMAZE(1), DCB(2), BILINEAR(3), VNG4(4), AHD(5), LMMSE(6)
    }

    enum class WBIlluminant(val nativeValue: Int) {
        DAYLIGHT(0), TUNGSTEN(1), FLUORESCENT(2), FLASH(3),
        CLOUDY(4), SHADE(5), CAMERA_AUTO(6), AS_SHOT(8)
    }

    sealed class DecodeProgress {
        object Idle : DecodeProgress()
        data class Running(val jobId: Long, val progress: Float, val stage: String) : DecodeProgress()
        data class Completed(val jobId: Long, val result: Any) : DecodeProgress()
        data class Failed(val jobId: Long, val error: String) : DecodeProgress()
    }

    private val _decodeProgress = MutableStateFlow<DecodeProgress>(DecodeProgress.Idle)
    val decodeProgress: StateFlow<DecodeProgress> = _decodeProgress

    fun detectFormat(filePath: String): String = "unknown"
    fun isRawFormat(filePath: String): Boolean = false
    suspend fun readRawInfo(filePath: String): RawImageInfo? = null
    suspend fun decodeRaw(filePath: String, options: DecodeOptions = DecodeOptions()): DecodedRaw? = null
    suspend fun extractMetadata(filePath: String): DecodedMetadata? = null
    suspend fun generateThumbnail(filePath: String, maxDimension: Int = 256): Bitmap? = null
    fun clearMetadataCache() {}
    fun clearThumbnailCache() {}
    fun clearRawInfoCache() {}
    fun clearAllCaches() {}
    fun evictFromCache(filePath: String) {}
    fun cancelDecode(jobId: Long) {}
    fun cancelAll() {}
    fun onDestroy() {}
}

data class NativeDecodeResult(
    val rgbFloatData: FloatArray,
    val rgbShortData: ShortArray?,
    val cfaData: ShortArray?,
    val width: Int,
    val height: Int,
    val rawInfo: DecodeService.RawImageInfo,
    val thumbnailData: ByteArray?,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val previewData: ByteArray?,
    val previewWidth: Int,
    val previewHeight: Int
)

data class NativeThumbnailResult(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val isEmbedded: Boolean
)

data class NativeRawInfoResult(
    val format: String,
    val make: String,
    val model: String,
    val rawWidth: Int,
    val rawHeight: Int,
    val bayerPattern: Int,
    val whiteLevel: Int,
    val blackLevel: Int
)
