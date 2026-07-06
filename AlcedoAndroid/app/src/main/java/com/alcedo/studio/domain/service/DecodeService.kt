package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.alcedo.studio.ndk.AlcedoNativeBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * High-level decode service that bridges the JNI native decoder
 * with Kotlin coroutines. Supports RAW, metadata, and thumbnail
 * decoding with caching and cancellation.
 */
class DecodeService(
    private val decodeBridge: AlcedoNativeBridge = AlcedoNativeBridge
) {
    companion object {
        private const val MAX_CACHE_ENTRIES = 64
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val nextJobId = AtomicLong(1)

    // Bounded LRU caches for decode results (prevents unbounded memory growth)
    private val metadataCache: MutableMap<String, CachedMetadata> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedMetadata>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedMetadata>): Boolean =
                size > MAX_CACHE_ENTRIES
        }
    )
    private val thumbnailCache: MutableMap<String, CachedThumbnail> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedThumbnail>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedThumbnail>): Boolean =
                size > MAX_CACHE_ENTRIES
        }
    )
    private val rawInfoCache: MutableMap<String, CachedRawInfo> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedRawInfo>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedRawInfo>): Boolean =
                size > MAX_CACHE_ENTRIES
        }
    )

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
        val rawJson: String = "",
        // 国内相机品牌 MakerNotes（大疆/华为/小米/哈苏）
        val djiFlightHeight: Float = 0f,
        val djiGimbalPitch: Float = 0f,
        val djiGimbalRoll: Float = 0f,
        val djiGpsMode: String = "",
        val aiScene: String = "",
        val multiFrameCount: Int = 0,
        val aiEnhancement: String = "",
        val hasselbladColorProfile: String = ""
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
                // TIFF-based formats: little-endian ("II" / 0x49 0x49) or big-endian ("MM" / 0x4D 0x4D)
                (header[0] == 0x49.toByte() || header[0] == 0x4D.toByte()) -> {
                    // DNG is a TIFF-based format. Distinguish DNG from generic RAW/TIFF
                    // by looking for the DNGVersion tag (0xC612 / 50706) in the IFD, or
                    // fall back to the file extension.
                    detectTiffBasedFormat(file, header)
                }
                else -> "unknown"
            }
        }
    }

    /**
     * Distinguish DNG from generic TIFF/RAW among TIFF-based files.
     *
     * DNG files embed a DNGVersion tag (50706) in their main IFD. We scan the
     * first IFD for this tag. If found (or the extension is .dng), the file is
     * reported as "DNG"; otherwise it is reported as "RAW" (covers NEF, CR2,
     * ARW, etc.) or "TIFF" for plain TIFFs.
     */
    private fun detectTiffBasedFormat(file: File, header: ByteArray): String {
        val ext = file.extension.lowercase()
        // Quick path: trust the .dng extension for TIFF-based files.
        if (ext == "dng") return "DNG"
        if (ext == "tiff" || ext == "tif") return "TIFF"

        val isLittleEndian = header[0] == 0x49.toByte() // "II"
        // Verify TIFF magic (42 = 0x2A) at offset 2-3.
        val magic = if (isLittleEndian) {
            (header[3].toInt() and 0xFF shl 8) or (header[2].toInt() and 0xFF)
        } else {
            (header[2].toInt() and 0xFF shl 8) or (header[3].toInt() and 0xFF)
        }
        if (magic != 42 && magic != 43) return "RAW"

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Read IFD offset (4 bytes at offset 4)
                raf.seek(4)
                val ifdOffset = if (isLittleEndian) {
                    (raf.read().let { it and 0xFF }) or
                        (raf.read().let { it and 0xFF } shl 8) or
                        (raf.read().let { it and 0xFF } shl 16) or
                        (raf.read().let { it and 0xFF } shl 24)
                } else {
                    (raf.read().let { it and 0xFF } shl 24) or
                        (raf.read().let { it and 0xFF } shl 16) or
                        (raf.read().let { it and 0xFF } shl 8) or
                        (raf.read().let { it and 0xFF })
                }
                if (ifdOffset <= 0 || ifdOffset >= file.length()) return "RAW"

                raf.seek(ifdOffset.toLong())
                val entryCount = if (isLittleEndian) {
                    (raf.read().let { it and 0xFF }) or (raf.read().let { it and 0xFF } shl 8)
                } else {
                    (raf.read().let { it and 0xFF } shl 8) or (raf.read().let { it and 0xFF })
                }

                // Each IFD entry is 12 bytes; the first 2 bytes are the tag id.
                // DNGVersion tag = 50706 (0xC612).
                for (i in 0 until entryCount) {
                    val tag = if (isLittleEndian) {
                        (raf.read().let { it and 0xFF }) or (raf.read().let { it and 0xFF } shl 8)
                    } else {
                        (raf.read().let { it and 0xFF } shl 8) or (raf.read().let { it and 0xFF })
                    }
                    if (tag == 50706) return "DNG" // DNGVersion tag found
                    // Skip remaining 10 bytes of this entry
                    raf.skipBytes(10)
                }
            }
        } catch (_: Exception) {
            // Ignore; fall through to RAW
        }
        return "RAW"
    }

    /**
     * Detect the DNG sub-brand (DJI / Huawei / Xiaomi / Xiaomi-Leica) from the
     * embedded EXIF metadata. Returns null when the make is unknown or not a
     * recognized DNG sub-brand.
     *
     * - DJI DNG typically carries extra MakerNotes.
     * - Huawei / Xiaomi DNG may use a special color matrix.
     * - Xiaomi-Leica partnership lenses are reported via the lens model.
     */
    suspend fun detectDngSubBrand(filePath: String): String? {
        val metadata = runCatching {
            extractMetadata(filePath)
        }.getOrNull()
        val make = metadata?.cameraMake?.lowercase() ?: ""
        return when {
            make.contains("dji") -> "dji"
            make.contains("huawei") || make.contains("honor") -> "huawei"
            make.contains("xiaomi") || make.contains("redmi") -> "xiaomi"
            make.contains("leica") &&
                metadata?.lensModel?.contains("Leica", ignoreCase = true) == true -> "xiaomi_leica"
            else -> null
        }
    }

    // ============================================================
    // Read RAW image info (fast)
    // ============================================================

    suspend fun readRawInfo(filePath: String): RawImageInfo? = withContext(Dispatchers.IO) {
        rawInfoCache[filePath]?.let { return@withContext it.info }

        try {
            val nativeInfo = decodeBridge.nativeReadRawInfo(filePath)
            if (nativeInfo != null) {
                val info = RawImageInfo(
                    format = nativeInfo.format,
                    make = nativeInfo.make,
                    model = nativeInfo.model,
                    rawWidth = nativeInfo.rawWidth,
                    rawHeight = nativeInfo.rawHeight,
                    bayerPattern = nativeInfo.bayerPattern,
                    whiteLevel = nativeInfo.whiteLevel,
                    blackLevel = nativeInfo.blackLevel,
                    cameraToSrgb = nativeInfo.cameraToSrgb,
                    daylightWb = nativeInfo.daylightWb,
                    tungstenWb = nativeInfo.tungstenWb,
                    cameraWb = nativeInfo.cameraWb,
                    cfaPattern = nativeInfo.cfaPattern,
                    bitsPerSample = nativeInfo.bitsPerSample,
                    compressionType = nativeInfo.compressionType,
                    isNikonHe = nativeInfo.isNikonHe
                )
                rawInfoCache[filePath] = CachedRawInfo(info)
                info
            } else {
                null
            }
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
    ): DecodedRaw? = withContext(Dispatchers.IO) {
        val jobId = nextJobId.getAndIncrement()
        _decodeProgress.value = DecodeProgress.Running(jobId, 0f, "Starting RAW decode")

        try {
            val result = decodeBridge.nativeDecodeRawFull(
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
                val metadata = extractMetadata(filePath)
                DecodedRaw(
                    rgbData = result.rgbFloatData,
                    width = result.width,
                    height = result.height,
                    rawInfo = result.rawInfo,
                    thumbnailData = result.thumbnailData,
                    previewData = result.previewData,
                    metadata = metadata
                )
            } else {
                _decodeProgress.value = DecodeProgress.Failed(jobId, "Decode returned null")
                null
            }
        } catch (e: CancellationException) {
            _decodeProgress.value = DecodeProgress.Failed(jobId, "Cancelled")
            decodeBridge.nativeCancelDecode(jobId)
            null
        } catch (e: Exception) {
            _decodeProgress.value = DecodeProgress.Failed(jobId, e.message ?: "Unknown error")
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
            val make = obj.optString("make", "")
            // 国内相机品牌 MakerNotes 兼容解析
            val makernote = obj.optJSONObject("makernote")
            val dji = if (make.contains("DJI", ignoreCase = true)) {
                parseDjiMakerNotes(obj, makernote)
            } else null
            val ai = if (make.contains("HUAWEI", ignoreCase = true) ||
                make.contains("Xiaomi", ignoreCase = true)
            ) {
                parseHuaweiXiaomiMetadata(obj, makernote)
            } else null
            val hasselbladProfile = if (make.contains("Hasselblad", ignoreCase = true)) {
                parseHasselbladMetadata(obj, makernote)
            } else ""

            DecodedMetadata(
                cameraMake = make,
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
                rawJson = json,
                // 国内品牌 MakerNotes
                djiFlightHeight = dji?.flightHeight ?: 0f,
                djiGimbalPitch = dji?.gimbalPitch ?: 0f,
                djiGimbalRoll = dji?.gimbalRoll ?: 0f,
                djiGpsMode = dji?.gpsMode ?: "",
                aiScene = ai?.aiScene ?: "",
                multiFrameCount = ai?.multiFrameCount ?: 0,
                aiEnhancement = ai?.aiEnhancement ?: "",
                hasselbladColorProfile = hasselbladProfile
            )
        } catch (e: Exception) {
            DecodedMetadata()
        }
    }

    // ============================================================
    // 国内相机品牌 MakerNotes 解析
    // ============================================================

    private data class DjiMakerNotes(
        val flightHeight: Float = 0f,
        val gimbalPitch: Float = 0f,
        val gimbalRoll: Float = 0f,
        val gpsMode: String = ""
    )

    private data class AiMetadata(
        val aiScene: String = "",
        val multiFrameCount: Int = 0,
        val aiEnhancement: String = ""
    )

    /** 在 makernote 子对象中查找键，找不到时回退到顶层 JSON。 */
    private fun makernoteString(makernote: JSONObject?, obj: JSONObject, key: String): String {
        makernote?.let { if (it.has(key)) return it.optString(key, "") }
        return obj.optString(key, "")
    }

    private fun makernoteFloat(makernote: JSONObject?, obj: JSONObject, key: String): Float {
        makernote?.let { if (it.has(key)) return it.optDouble(key, 0.0).toFloat() }
        return obj.optDouble(key, 0.0).toFloat()
    }

    private fun makernoteInt(makernote: JSONObject?, obj: JSONObject, key: String): Int {
        makernote?.let { if (it.has(key)) return it.optInt(key, 0) }
        return obj.optInt(key, 0)
    }

    /**
     * 大疆 MakerNotes：包含飞行高度、云台俯仰/横滚角度、GPS 模式等。
     * 适用于 DJI 无人机（Mavic/Air/Mini 系列）及搭载相机。
     */
    private fun parseDjiMakerNotes(obj: JSONObject, makernote: JSONObject?): DjiMakerNotes {
        return DjiMakerNotes(
            flightHeight = makernoteFloat(makernote, obj, "FlightHeight"),
            gimbalPitch = makernoteFloat(makernote, obj, "GimbalPitch"),
            gimbalRoll = makernoteFloat(makernote, obj, "GimbalRoll"),
            gpsMode = makernoteString(makernote, obj, "GpsMode")
        )
    }

    /**
     * 华为/小米 MakerNotes：包含 AI 场景识别、多帧合成张数、AI 增强信息等。
     * 适用于华为 P/Mate 系列、小米数字系列等多帧合成机型。
     */
    private fun parseHuaweiXiaomiMetadata(obj: JSONObject, makernote: JSONObject?): AiMetadata {
        return AiMetadata(
            aiScene = makernoteString(makernote, obj, "AiScene"),
            multiFrameCount = makernoteInt(makernote, obj, "MultiFrameCount"),
            aiEnhancement = makernoteString(makernote, obj, "AiEnhancement")
        )
    }

    /**
     * 哈苏（国产版，大疆收购）MakerNotes：提取色彩配置信息。
     */
    private fun parseHasselbladMetadata(obj: JSONObject, makernote: JSONObject?): String {
        return makernoteString(makernote, obj, "HasselbladColorProfile")
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