package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.alcedo.studio.ndk.DecodeNdkBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * High-level decode service that bridges the JNI native decoder
 * with Kotlin coroutines. Supports RAW, metadata, and thumbnail
 * decoding with caching and cancellation.
 */
class DecodeService(
    private val decodeBridge: DecodeNdkBridge = DecodeNdkBridge
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val nextJobId = AtomicLong(1)

    // Cache for decode results
    private val metadataCache = ConcurrentHashMap<String, CachedMetadata>()
    private val thumbnailCache = ConcurrentHashMap<String, CachedThumbnail>()
    private val rawInfoCache = ConcurrentHashMap<String, CachedRawInfo>()

    // Progress tracking
    private val _decodeProgress = MutableStateFlow<DecodeProgress>(DecodeProgress.Idle)
    val decodeProgress: StateFlow<DecodeProgress> = _decodeProgress

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
        val blackLevel: Int = 0,
        val cameraToSrgb: FloatArray = FloatArray(9) { if (it % 4 == 0) 1f else 0f },
        val daylightWb: FloatArray = FloatArray(3) { 1f },
        val tungstenWb: FloatArray = FloatArray(3) { 1f },
        val cameraWb: FloatArray = FloatArray(3) { 1f },
        val cfaPattern: IntArray = intArrayOf(0, 1, 1, 2),
        val bitsPerSample: Int = 16,
        val compressionType: String = "none",
        val isNikonHe: Boolean = false
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

    private data class CachedMetadata(val metadata: DecodedMetadata, val timestamp: Long = System.currentTimeMillis())
    private data class CachedThumbnail(val data: ByteArray, val width: Int, val height: Int, val timestamp: Long = System.currentTimeMillis())
    private data class CachedRawInfo(val info: RawImageInfo, val timestamp: Long = System.currentTimeMillis())

    // ============================================================
    // Format detection
    // ============================================================

    fun detectFormat(filePath: String): String {
        return try {
            decodeBridge.nativeDetectFormat(filePath)
        } catch (e: Exception) {
            detectFormatFallback(filePath)
        }
    }

    fun isRawFormat(filePath: String): Boolean {
        return try {
            decodeBridge.nativeIsRawFormat(filePath)
        } catch (e: Exception) {
            val fmt = detectFormatFallback(filePath)
            fmt in setOf("DNG", "NEF", "CR2", "CR3", "ARW", "RAF", "ORF", "RW2", "PEF", "SRW", "3FR", "IIQ")
        }
    }

    private fun detectFormatFallback(path: String): String {
        val file = File(path)
        if (!file.exists() || file.length() < 8) return "unknown"
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(8)
            raf.readFully(header)
            return when {
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> "JPEG"
                header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() -> "PNG"
                (header[0] == 0x49.toByte() || header[0] == 0x4D.toByte()) -> "RAW"
                else -> "unknown"
            }
        }
    }

    // ============================================================
    // Read RAW image info (fast)
    // ============================================================

    suspend fun readRawInfo(filePath: String): RawImageInfo? = withContext(Dispatchers.IO) {
        rawInfoCache[filePath]?.let { return@withContext it.info }

        try {
            val info = decodeBridge.nativeReadRawInfo(filePath)
            rawInfoCache[filePath] = CachedRawInfo(info)
            info
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    // Full RAW decode (with cancellation)
    // ============================================================

    suspend fun decodeRaw(
        filePath: String,
        options: DecodeOptions = DecodeOptions()
    ): DecodedRaw? {
        val jobId = nextJobId.getAndIncrement()
        val job = scope.launch {
            _decodeProgress.value = DecodeProgress.Running(jobId, 0f, "Starting RAW decode")

            try {
                val result = decodeBridge.nativeDecodeRaw(
                    filePath,
                    options.demosaic.nativeValue,
                    options.highlightReconstruction,
                    options.useCameraMatrix,
                    options.halfResolution,
                    options.outputFloat,
                    options.extractThumbnail,
                    options.extractPreview,
                    options.maxThumbnailDimension,
                    options.whiteBalanceIlluminant.nativeValue
                )

                if (result != null) {
                    _decodeProgress.value = DecodeProgress.Completed(jobId, result)
                } else {
                    _decodeProgress.value = DecodeProgress.Failed(jobId, "Decode returned null")
                }
            } catch (e: CancellationException) {
                _decodeProgress.value = DecodeProgress.Failed(jobId, "Cancelled")
                throw e
            } catch (e: Exception) {
                _decodeProgress.value = DecodeProgress.Failed(jobId, e.message ?: "Unknown error")
            }
        }

        activeJobs[jobId] = job

        return try {
            val result = decodeBridge.nativeDecodeRaw(
                filePath,
                options.demosaic.nativeValue,
                options.highlightReconstruction,
                options.useCameraMatrix,
                options.halfResolution,
                options.outputFloat,
                options.extractThumbnail,
                options.extractPreview,
                options.maxThumbnailDimension,
                options.whiteBalanceIlluminant.nativeValue
            )

            val metadata = extractMetadata(filePath)
            result?.let { r ->
                DecodedRaw(
                    rgbData = r.rgbFloatData,
                    width = r.width,
                    height = r.height,
                    rawInfo = r.rawInfo,
                    thumbnailData = r.thumbnailData,
                    previewData = r.previewData,
                    metadata = metadata
                )
            }
        } catch (e: CancellationException) {
            decodeBridge.nativeCancelDecode(jobId)
            null
        }
    }

    // ============================================================
    // Metadata extraction
    // ============================================================

    suspend fun extractMetadata(filePath: String): DecodedMetadata? = withContext(Dispatchers.IO) {
        metadataCache[filePath]?.let { return@withContext it.metadata }

        try {
            val json = decodeBridge.nativeExtractMetadata(filePath) ?: return@withContext null
            val metadata = parseMetadataJson(json, filePath)
            metadataCache[filePath] = CachedMetadata(metadata)
            metadata
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMetadataJson(json: String, filePath: String): DecodedMetadata {
        return try {
            val obj = JSONObject(json)
            DecodedMetadata(
                cameraMake = obj.optString("make", ""),
                cameraModel = obj.optString("model", ""),
                lensModel = obj.optString("lens", ""),
                focalLength = obj.optDouble("focal", 0.0).toFloat(),
                aperture = obj.optDouble("aperture", 0.0).toFloat(),
                shutterSpeed = obj.optDouble("shutter", 0.0).toFloat(),
                iso = obj.optDouble("iso", 0.0).toFloat(),
                exposureBias = obj.optDouble("exposure_bias", 0.0).toFloat(),
                imageWidth = obj.optInt("width", 0),
                imageHeight = obj.optInt("height", 0),
                orientation = obj.optInt("orientation", 1),
                dateTime = obj.optString("datetime", ""),
                colorSpace = obj.optString("color_space", ""),
                hasGps = obj.optBoolean("has_gps", false),
                gpsLatitude = obj.optDouble("gps_lat", 0.0),
                gpsLongitude = obj.optDouble("gps_lon", 0.0),
                gpsAltitude = obj.optDouble("gps_altitude", 0.0),
                rawJson = json
            )
        } catch (e: Exception) {
            DecodedMetadata()
        }
    }

    // ============================================================
    // Thumbnail generation
    // ============================================================

    suspend fun generateThumbnail(
        filePath: String,
        maxDimension: Int = 256
    ): Bitmap? = withContext(Dispatchers.IO) {
        // Check in-memory cache
        thumbnailCache[filePath]?.let { cached ->
            return@withContext BitmapFactory.decodeByteArray(cached.data, 0, cached.data.size)
        }

        try {
            val result = decodeBridge.nativeGenerateThumbnail(filePath, maxDimension, true)
            if (result != null && result.data.isNotEmpty()) {
                thumbnailCache[filePath] = CachedThumbnail(result.data, result.width, result.height)
                BitmapFactory.decodeByteArray(result.data, 0, result.data.size)
            } else {
                // Fallback: extract embedded preview via Android's built-in
                generateFallbackThumbnail(filePath, maxDimension)
            }
        } catch (e: Exception) {
            generateFallbackThumbnail(filePath, maxDimension)
        }
    }

    suspend fun generateThumbnailAsync(
        filePath: String,
        maxDimension: Int = 256,
        onResult: (Bitmap?) -> Unit
    ) {
        scope.launch {
            val bitmap = generateThumbnail(filePath, maxDimension)
            withContext(Dispatchers.Main) {
                onResult(bitmap)
            }
        }
    }

    private fun generateFallbackThumbnail(path: String, maxDimension: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            val scale = maxOf(1, maxOf(options.outWidth / maxDimension, options.outHeight / maxDimension))
            BitmapFactory.Options().apply {
                inSampleSize = scale
            }.let { BitmapFactory.decodeFile(path, it) }
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    // Batch decode
    // ============================================================

    suspend fun batchExtractMetadata(paths: List<String>): Map<String, DecodedMetadata> =
        withContext(Dispatchers.IO) {
            paths.map { path ->
                async {
                    path to extractMetadata(path)
                }
            }.mapNotNull { it.await() }
                .filter { it.second != null }
                .associate { it.first to it.second!! }
        }

    suspend fun batchGenerateThumbnails(
        paths: List<String>,
        maxDimension: Int = 256,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<String, Bitmap?> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Bitmap?>()
        var completed = 0
        paths.map { path ->
            async {
                val bitmap = generateThumbnail(path, maxDimension)
                synchronized(results) {
                    results[path] = bitmap
                    completed++
                    onProgress(completed, paths.size)
                }
            }
        }.awaitAll()
        results
    }

    // ============================================================
    // Cache management
    // ============================================================

    fun clearMetadataCache() {
        metadataCache.clear()
    }

    fun clearThumbnailCache() {
        thumbnailCache.clear()
    }

    fun clearRawInfoCache() {
        rawInfoCache.clear()
    }

    fun clearAllCaches() {
        metadataCache.clear()
        thumbnailCache.clear()
        rawInfoCache.clear()
    }

    fun evictFromCache(filePath: String) {
        metadataCache.remove(filePath)
        thumbnailCache.remove(filePath)
        rawInfoCache.remove(filePath)
    }

    // ============================================================
    // Cancellation
    // ============================================================

    fun cancelDecode(jobId: Long) {
        activeJobs[jobId]?.cancel()
        activeJobs.remove(jobId)
        decodeBridge.nativeCancelDecode(jobId)
    }

    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        decodeBridge.nativeCancelAllDecodes()
    }

    fun onDestroy() {
        scope.cancel()
        clearAllCaches()
    }
}

// ============================================================
// Native result data classes (mirrors JNI bridge return types)
// ============================================================

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
    val blackLevel: Int,
    val cameraToSrgb: FloatArray,
    val daylightWb: FloatArray,
    val tungstenWb: FloatArray,
    val cameraWb: FloatArray,
    val cfaPattern: IntArray,
    val bitsPerSample: Int,
    val compressionType: String,
    val isNikonHe: Boolean
)