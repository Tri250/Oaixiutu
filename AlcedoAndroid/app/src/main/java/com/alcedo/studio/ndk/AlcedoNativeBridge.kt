package com.alcedo.studio.ndk

import android.util.Log
import com.alcedo.studio.domain.service.NativeDecodeResult
import com.alcedo.studio.domain.service.NativeRawInfoResult
import com.alcedo.studio.domain.service.NativeThumbnailResult

/**
 * Unified NDK bridge for all native operations.
 * Consolidates AlcedoNdkBridge, DecodeNdkBridge, SleeveNdkBridge, and NativePipelineBridge
 * into a single entry point with one-time library loading and safe call wrappers.
 */
object AlcedoNativeBridge {
    private const val TAG = "AlcedoNativeBridge"

    @Volatile
    var isAvailable = false
        private set

    // Single library load with thread-safe double-checked locking
    init {
        synchronized(this) {
            if (!isAvailable) {
                try {
                    System.loadLibrary("alcedo")
                    isAvailable = true
                    Log.i(TAG, "Native library loaded successfully")
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to load native library", e)
                    isAvailable = false
                }
            }
        }
    }

    // ================================================================
    // Basic utilities (from AlcedoNdkBridge)
    // ================================================================

    external fun stringFromJNI(): String
    external fun nativeInitialize()
    external fun nativeGenerateId(): Long
    external fun nativeGetTimestampMillis(): Long
    external fun nativeGetTimestampMicros(): Long
    external fun nativeSetLogLevel(level: Int)

    fun initialize(): Boolean {
        if (!isAvailable) {
            Log.e(TAG, "initialize() failed: native library not available")
            return false
        }
        return try {
            nativeInitialize()
            Log.i(TAG, "initialize() succeeded")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "initialize() failed", e)
            false
        }
    }

    fun generateId(): Long {
        if (!isAvailable) return 0L
        return NdkSafeCall.execute("generateId") {
            nativeGenerateId()
        } ?: 0L
    }

    fun getTimestampMillis(): Long {
        if (!isAvailable) return 0L
        return NdkSafeCall.execute("getTimestampMillis") {
            nativeGetTimestampMillis()
        } ?: 0L
    }

    fun getTimestampMicros(): Long {
        if (!isAvailable) return 0L
        return NdkSafeCall.execute("getTimestampMicros") {
            nativeGetTimestampMicros()
        } ?: 0L
    }

    fun setLogLevel(level: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("setLogLevel") {
            nativeSetLogLevel(level)
        }
    }

    // ================================================================
    // Format detection (from DecodeNdkBridge)
    // ================================================================

    external fun nativeDetectFormat(filePath: String): String
    external fun nativeIsRawFormat(filePath: String): Boolean
    external fun nativeReadRawInfo(filePath: String): NativeRawInfoResult?

    fun detectFormat(filePath: String): String {
        if (!isAvailable) return "unknown"
        return NdkSafeCall.execute("detectFormat") {
            nativeDetectFormat(filePath)
        } ?: "unknown"
    }

    fun isRawImage(filePath: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("isRawFormat") {
            nativeIsRawFormat(filePath)
        }
    }

    fun readRawInfo(filePath: String): NativeRawInfoResult? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("readRawInfo") {
            nativeReadRawInfo(filePath)
        }
    }

    // ================================================================
    // RAW decode (from DecodeNdkBridge) - full decode with all options
    // ================================================================

    external fun nativeDecodeRawFull(
        filePath: String, demosaic: Int, highlightReconstruction: Boolean,
        useCameraMatrix: Boolean, halfResolution: Boolean, outputFloat: Boolean,
        extractThumbnail: Boolean, extractPreview: Boolean, maxThumbnailDim: Int,
        wbIlluminant: Int
    ): NativeDecodeResult?

    external fun nativeDecodeRawFromMemory(
        data: ByteArray, demosaic: Int, highlightReconstruction: Boolean,
        useCameraMatrix: Boolean, halfResolution: Boolean, outputFloat: Boolean
    ): NativeDecodeResult?

    fun decodeRawFull(
        filePath: String, demosaic: Int, highlightReconstruction: Boolean,
        useCameraMatrix: Boolean, halfResolution: Boolean, outputFloat: Boolean,
        extractThumbnail: Boolean, extractPreview: Boolean, maxThumbnailDim: Int,
        wbIlluminant: Int
    ): NativeDecodeResult? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("decodeRawFull") {
            nativeDecodeRawFull(filePath, demosaic, highlightReconstruction, useCameraMatrix,
                halfResolution, outputFloat, extractThumbnail, extractPreview, maxThumbnailDim, wbIlluminant)
        }
    }

    fun decodeRawFromMemory(
        data: ByteArray, demosaic: Int, highlightReconstruction: Boolean,
        useCameraMatrix: Boolean, halfResolution: Boolean, outputFloat: Boolean
    ): NativeDecodeResult? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("decodeRawFromMemory") {
            nativeDecodeRawFromMemory(data, demosaic, highlightReconstruction, useCameraMatrix,
                halfResolution, outputFloat)
        }
    }

    // ================================================================
    // RAW decode (from NativePipelineBridge) - simple decode
    // ================================================================

    external fun nativeDecodeRawSimple(
        rawPath: String,
        demosaic: Int,
        highlightReconstruction: Boolean
    ): IntArray

    external fun nativeDecodeRawFloat(
        rawData: ShortArray,
        rawWidth: Int,
        rawHeight: Int,
        bayerPattern: Int,
        whiteLevel: Int,
        blackLevel: Int,
        highlightReconstruction: Boolean
    ): FloatArray

    fun decodeRawSimple(rawPath: String, demosaic: Int, highlightReconstruction: Boolean): IntArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("decodeRawSimple") {
            nativeDecodeRawSimple(rawPath, demosaic, highlightReconstruction)
        }
    }

    fun decodeRawFloat(
        rawData: ShortArray, rawWidth: Int, rawHeight: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int,
        highlightReconstruction: Boolean
    ): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("decodeRawFloat") {
            nativeDecodeRawFloat(rawData, rawWidth, rawHeight, bayerPattern, whiteLevel, blackLevel, highlightReconstruction)
        }
    }

    // ================================================================
    // Metadata extraction (from DecodeNdkBridge)
    // ================================================================

    external fun nativeExtractMetadata(filePath: String): String?
    external fun nativeExtractMetadataFromMemory(data: ByteArray): String?
    external fun nativeExtractExif(filePath: String): String?
    external fun nativeExtractXmp(filePath: String): String?
    external fun nativeExtractIccProfile(filePath: String): ByteArray?
    external fun nativeExtractDngColor(filePath: String): FloatArray?

    fun extractMetadata(filePath: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractMetadata") {
            nativeExtractMetadata(filePath)
        }
    }

    fun extractMetadataFromMemory(data: ByteArray): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractMetadataFromMemory") {
            nativeExtractMetadataFromMemory(data)
        }
    }

    fun extractExif(filePath: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractExif") {
            nativeExtractExif(filePath)
        }
    }

    fun extractXmp(filePath: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractXmp") {
            nativeExtractXmp(filePath)
        }
    }

    fun extractIccProfile(filePath: String): ByteArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractIccProfile") {
            nativeExtractIccProfile(filePath)
        }
    }

    fun extractDngColor(filePath: String): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractDngColor") {
            nativeExtractDngColor(filePath)
        }
    }

    // ================================================================
    // Metadata extraction (from NativePipelineBridge) - legacy
    // ================================================================

    external fun nativeExtractMetadataLegacy(filePath: String): String

    fun extractMetadataLegacy(filePath: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractMetadataLegacy") {
            nativeExtractMetadataLegacy(filePath)
        }
    }

    // ================================================================
    // Thumbnail generation (from DecodeNdkBridge)
    // ================================================================

    external fun nativeGenerateThumbnail(filePath: String, maxDimension: Int, useEmbedded: Boolean): NativeThumbnailResult?
    external fun nativeGenerateThumbnailFromRGB(rgbData: FloatArray, width: Int, height: Int, maxDimension: Int): ByteArray?
    external fun nativeExtractEmbeddedThumbnail(filePath: String): ByteArray?
    external fun nativeExtractEmbeddedPreview(filePath: String): ByteArray?

    fun generateThumbnail(filePath: String, maxDimension: Int, useEmbedded: Boolean): NativeThumbnailResult? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("generateThumbnail") {
            nativeGenerateThumbnail(filePath, maxDimension, useEmbedded)
        }
    }

    fun generateThumbnailFromRGB(rgbData: FloatArray, width: Int, height: Int, maxDimension: Int): ByteArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("generateThumbnailFromRGB") {
            nativeGenerateThumbnailFromRGB(rgbData, width, height, maxDimension)
        }
    }

    fun extractEmbeddedThumbnail(filePath: String): ByteArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractEmbeddedThumbnail") {
            nativeExtractEmbeddedThumbnail(filePath)
        }
    }

    fun extractEmbeddedPreview(filePath: String): ByteArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractEmbeddedPreview") {
            nativeExtractEmbeddedPreview(filePath)
        }
    }

    // ================================================================
    // Demosaic (from DecodeNdkBridge)
    // ================================================================

    external fun nativeDemosaic(
        rawCfaData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int, demosaicMethod: Int
    ): FloatArray?

    fun demosaic(
        rawCfaData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int, demosaicMethod: Int
    ): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("demosaic") {
            nativeDemosaic(rawCfaData, width, height, bayerPattern, whiteLevel, blackLevel, demosaicMethod)
        }
    }

    // ================================================================
    // White balance / Color matrix / Highlight / Black level (from DecodeNdkBridge)
    // ================================================================

    external fun nativeApplyWhiteBalance(rgbData: FloatArray, width: Int, height: Int, rMultiplier: Float, gMultiplier: Float, bMultiplier: Float)
    external fun nativeApplyColorMatrix(rgbData: FloatArray, width: Int, height: Int, matrix: FloatArray)
    external fun nativeReconstructHighlights(rgbData: FloatArray, width: Int, height: Int, whiteLevel: Int, mode: Int)
    external fun nativeSubtractBlackLevel(rawData: ShortArray, width: Int, height: Int, blackLevelR: Int, blackLevelG1: Int, blackLevelG2: Int, blackLevelB: Int)

    fun applyWhiteBalance(rgbData: FloatArray, width: Int, height: Int, rMultiplier: Float, gMultiplier: Float, bMultiplier: Float) {
        if (!isAvailable) return
        if (rgbData.isEmpty()) {
            Log.w(TAG, "applyWhiteBalance: rgbData is empty, skipping")
            return
        }
        val expectedSize = width * height * 3
        if (rgbData.size < expectedSize) {
            Log.w(TAG, "applyWhiteBalance: rgbData size=${rgbData.size} is smaller than expected=$expectedSize (w=$width h=$height), skipping")
            return
        }
        NdkSafeCall.execute("applyWhiteBalance") {
            nativeApplyWhiteBalance(rgbData, width, height, rMultiplier, gMultiplier, bMultiplier)
        }
    }

    fun applyColorMatrix(rgbData: FloatArray, width: Int, height: Int, matrix: FloatArray) {
        if (!isAvailable) return
        NdkSafeCall.execute("applyColorMatrix") {
            nativeApplyColorMatrix(rgbData, width, height, matrix)
        }
    }

    fun reconstructHighlights(rgbData: FloatArray, width: Int, height: Int, whiteLevel: Int, mode: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("reconstructHighlights") {
            nativeReconstructHighlights(rgbData, width, height, whiteLevel, mode)
        }
    }

    fun subtractBlackLevel(rawData: ShortArray, width: Int, height: Int, blackLevelR: Int, blackLevelG1: Int, blackLevelG2: Int, blackLevelB: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("subtractBlackLevel") {
            nativeSubtractBlackLevel(rawData, width, height, blackLevelR, blackLevelG1, blackLevelG2, blackLevelB)
        }
    }

    // ================================================================
    // Cancellation & Scheduler (from DecodeNdkBridge)
    // ================================================================

    external fun nativeCancelDecode(jobId: Long)
    external fun nativeCancelAllDecodes()
    external fun nativeInitScheduler(threadCount: Int)
    external fun nativeShutdownScheduler()
    external fun nativeSetProgressCallback(callback: Any?)

    fun cancelDecode(jobId: Long) {
        if (!isAvailable) return
        if (jobId < 0) {
            Log.w(TAG, "cancelDecode: invalid jobId=$jobId, must be >= 0")
            return
        }
        NdkSafeCall.execute("cancelDecode") {
            nativeCancelDecode(jobId)
        }
    }

    fun cancelAllDecodes() {
        if (!isAvailable) return
        NdkSafeCall.execute("cancelAllDecodes") {
            nativeCancelAllDecodes()
        }
    }

    fun initScheduler(threadCount: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("initScheduler") {
            nativeInitScheduler(threadCount)
        }
    }

    fun shutdownScheduler() {
        if (!isAvailable) return
        NdkSafeCall.execute("shutdownScheduler") {
            nativeShutdownScheduler()
        }
    }

    fun setProgressCallback(callback: Any?) {
        if (!isAvailable) return
        NdkSafeCall.execute("setProgressCallback") {
            nativeSetProgressCallback(callback)
        }
    }

    // ================================================================
    // Sleeve file system operations (from SleeveNdkBridge)
    // ================================================================

    external fun nativeInitializeSleeve()
    external fun nativeCreateFolder(path: String, name: String): Int
    external fun nativeCreateFile(path: String, name: String): Int
    external fun nativeDeleteElement(path: String): Boolean
    external fun nativeMoveElement(src: String, dst: String): Boolean
    external fun nativeCopyElement(src: String, dst: String): Boolean
    external fun nativeListFolder(path: String): IntArray?
    external fun nativeResolvePath(path: String): String

    fun initializeSleeve() {
        if (!isAvailable) return
        NdkSafeCall.execute("sleeveInitialize") {
            nativeInitializeSleeve()
        }
    }

    fun createFolder(parentPath: String, name: String): Long {
        if (!isAvailable) return -1L
        return NdkSafeCall.execute("createFolder") {
            nativeCreateFolder(parentPath, name).toLong()
        } ?: -1L
    }

    fun createFile(parentPath: String, name: String): Long {
        if (!isAvailable) return -1L
        return NdkSafeCall.execute("createFile") {
            nativeCreateFile(parentPath, name).toLong()
        } ?: -1L
    }

    fun createElement(parentPath: String, name: String, isFolder: Boolean): Long {
        if (!isAvailable) return -1L
        return if (isFolder) {
            createFolder(parentPath, name)
        } else {
            createFile(parentPath, name)
        }
    }

    fun deleteElement(path: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveDeleteElement") {
            nativeDeleteElement(path)
        }
    }

    fun moveElement(src: String, dst: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveMoveElement") {
            nativeMoveElement(src, dst)
        }
    }

    fun copyElement(src: String, dst: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveCopyElement") {
            nativeCopyElement(src, dst)
        }
    }

    fun listFolder(path: String): List<Long> {
        if (!isAvailable) return emptyList()
        val ids = NdkSafeCall.execute("sleeveListFolder") {
            nativeListFolder(path)
        } ?: return emptyList()
        return ids.map { it.toLong() }
    }

    fun resolvePath(path: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("sleeveResolvePath") {
            nativeResolvePath(path)
        }
    }

    // ================================================================
    // Pipeline operations (from NativePipelineBridge)
    // ================================================================

    external fun nativeApplyPipeline(
        input: IntArray,
        width: Int,
        height: Int,
        exposure: Float,
        contrast: Float,
        saturation: Float,
        highlights: Float,
        shadows: Float,
        temperature: Float,
        tint: Float,
        sharpen: Float
    ): IntArray

    external fun nativeApplyPipelineFloat(
        input: FloatArray,
        width: Int,
        height: Int,
        channels: Int,
        paramsArray: FloatArray
    ): FloatArray

    fun applyPipeline(
        input: IntArray, width: Int, height: Int,
        exposure: Float, contrast: Float, saturation: Float,
        highlights: Float, shadows: Float, temperature: Float, tint: Float, sharpen: Float
    ): IntArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyPipeline") {
            nativeApplyPipeline(input, width, height, exposure, contrast, saturation,
                highlights, shadows, temperature, tint, sharpen)
        }
    }

    fun applyPipelineFloat(
        input: FloatArray, width: Int, height: Int, channels: Int, paramsArray: FloatArray
    ): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyPipelineFloat") {
            nativeApplyPipelineFloat(input, width, height, channels, paramsArray)
        }
    }

    // ================================================================
    // P2-8 锐化蒙版可视化 (边缘蒙版生成)
    // ================================================================

    /** 生成边缘蒙版用于锐化可视化（输出为单通道 FloatArray，长度 = width * height） */
    external fun nativeGenerateEdgeMask(
        input: FloatArray, width: Int, height: Int,
        radius: Float, threshold: Float
    ): FloatArray

    /**
     * 生成边缘蒙版。使用边缘检测生成锐化蒙版预览，
     * [radius] 控制检测范围，[threshold] 控制响应灵敏度。
     * 返回 null 表示原生层不可用或调用失败。
     */
    fun generateEdgeMask(
        input: FloatArray, width: Int, height: Int, radius: Float, threshold: Float
    ): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.executeFloatArray("generateEdgeMask") {
            nativeGenerateEdgeMask(input, width, height, radius, threshold)
        }
    }

    // ================================================================
    // Color science transforms (from NativePipelineBridge)
    // ================================================================

    external fun nativeApplyAcesTransform(
        input: FloatArray, width: Int, height: Int, peakLuminance: Float
    ): FloatArray

    external fun nativeApplyOpenDRTTransform(
        input: FloatArray, width: Int, height: Int, peakLuminance: Float
    ): FloatArray

    external fun nativeConvertColorSpace(
        input: FloatArray, width: Int, height: Int, srcSpace: Int, dstSpace: Int
    ): FloatArray

    external fun nativeApplyEOTF(
        input: FloatArray, width: Int, height: Int, eotfType: Int, peakLuminance: Float
    ): FloatArray

    external fun nativeApplyLut(
        pixels: FloatArray, width: Int, height: Int, lutPath: String
    ): Boolean

    fun applyAcesTransform(input: FloatArray, width: Int, height: Int, peakLuminance: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyAcesTransform") {
            nativeApplyAcesTransform(input, width, height, peakLuminance)
        }
    }

    fun applyOpenDRTTransform(input: FloatArray, width: Int, height: Int, peakLuminance: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyOpenDRTTransform") {
            nativeApplyOpenDRTTransform(input, width, height, peakLuminance)
        }
    }

    fun convertColorSpace(input: FloatArray, width: Int, height: Int, srcSpace: Int, dstSpace: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("convertColorSpace") {
            nativeConvertColorSpace(input, width, height, srcSpace, dstSpace)
        }
    }

    fun applyEOTF(input: FloatArray, width: Int, height: Int, eotfType: Int, peakLuminance: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyEOTF") {
            nativeApplyEOTF(input, width, height, eotfType, peakLuminance)
        }
    }

    fun applyLut(pixels: FloatArray, width: Int, height: Int, lutPath: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("applyLut") {
            nativeApplyLut(pixels, width, height, lutPath)
        }
    }

    // ================================================================
    // OKLab conversions (from NativePipelineBridge)
    // ================================================================

    external fun nativeSrgbToOklab(r: Float, g: Float, b: Float): FloatArray
    external fun nativeOklabToSrgb(L: Float, a: Float, bb: Float): FloatArray

    fun srgbToOklab(r: Float, g: Float, b: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("srgbToOklab") {
            nativeSrgbToOklab(r, g, b)
        }
    }

    fun oklabToSrgb(L: Float, a: Float, bb: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("oklabToSrgb") {
            nativeOklabToSrgb(L, a, bb)
        }
    }

    // ================================================================
    // Tone operators (from NativePipelineBridge)
    // ================================================================

    external fun nativeApplySigmoidContrast(
        input: FloatArray, width: Int, height: Int, contrast: Float, pivot: Float, shoulder: Float
    ): FloatArray

    external fun nativeApplyBlackOp(
        input: FloatArray, width: Int, height: Int, channels: Int, blackPoint: Float
    ): FloatArray

    external fun nativeApplyWhiteOp(
        input: FloatArray, width: Int, height: Int, channels: Int, whitePoint: Float
    ): FloatArray

    external fun nativeApplyShadowOp(
        input: FloatArray, width: Int, height: Int, channels: Int, shadowAmount: Float
    ): FloatArray

    external fun nativeApplyHighlightOp(
        input: FloatArray, width: Int, height: Int, channels: Int, highlightAmount: Float
    ): FloatArray

    fun applySigmoidContrast(input: FloatArray, width: Int, height: Int, contrast: Float, pivot: Float, shoulder: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applySigmoidContrast") {
            nativeApplySigmoidContrast(input, width, height, contrast, pivot, shoulder)
        }
    }

    fun applyBlackOp(input: FloatArray, width: Int, height: Int, channels: Int, blackPoint: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyBlackOp") {
            nativeApplyBlackOp(input, width, height, channels, blackPoint)
        }
    }

    fun applyWhiteOp(input: FloatArray, width: Int, height: Int, channels: Int, whitePoint: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyWhiteOp") {
            nativeApplyWhiteOp(input, width, height, channels, whitePoint)
        }
    }

    fun applyShadowOp(input: FloatArray, width: Int, height: Int, channels: Int, shadowAmount: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyShadowOp") {
            nativeApplyShadowOp(input, width, height, channels, shadowAmount)
        }
    }

    fun applyHighlightOp(input: FloatArray, width: Int, height: Int, channels: Int, highlightAmount: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyHighlightOp") {
            nativeApplyHighlightOp(input, width, height, channels, highlightAmount)
        }
    }

    // ================================================================
    // Geometry operators (from NativePipelineBridge)
    // ================================================================

    external fun nativeApplyCrop(
        input: FloatArray, width: Int, height: Int, channels: Int,
        left: Int, top: Int, right: Int, bottom: Int
    ): FloatArray

    external fun nativeApplyRotate(
        input: FloatArray, width: Int, height: Int, channels: Int, angle: Int
    ): FloatArray

    external fun nativeApplyResize(
        input: FloatArray, width: Int, height: Int, channels: Int,
        dstWidth: Int, dstHeight: Int, method: Int
    ): FloatArray

    fun applyCrop(input: FloatArray, width: Int, height: Int, channels: Int,
                  left: Int, top: Int, right: Int, bottom: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyCrop") {
            nativeApplyCrop(input, width, height, channels, left, top, right, bottom)
        }
    }

    fun applyRotate(input: FloatArray, width: Int, height: Int, channels: Int, angle: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyRotate") {
            nativeApplyRotate(input, width, height, channels, angle)
        }
    }

    fun applyResize(input: FloatArray, width: Int, height: Int, channels: Int,
                    dstWidth: Int, dstHeight: Int, method: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyResize") {
            nativeApplyResize(input, width, height, channels, dstWidth, dstHeight, method)
        }
    }

    // ================================================================
    // Auto exposure (from NativePipelineBridge)
    // ================================================================

    external fun nativeComputeAutoExposure(
        input: FloatArray, width: Int, height: Int, channels: Int,
        targetPercentile: Float, targetLuminance: Float
    ): Float

    external fun nativeApplyAutoExposure(
        input: FloatArray, width: Int, height: Int, channels: Int,
        targetPercentile: Float, targetLuminance: Float
    ): FloatArray

    fun computeAutoExposure(input: FloatArray, width: Int, height: Int, channels: Int,
                            targetPercentile: Float, targetLuminance: Float): Float {
        if (!isAvailable) return 0f
        return NdkSafeCall.executeFloat("computeAutoExposure") {
            nativeComputeAutoExposure(input, width, height, channels, targetPercentile, targetLuminance)
        }
    }

    fun applyAutoExposure(input: FloatArray, width: Int, height: Int, channels: Int,
                          targetPercentile: Float, targetLuminance: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyAutoExposure") {
            nativeApplyAutoExposure(input, width, height, channels, targetPercentile, targetLuminance)
        }
    }

    // ================================================================
    // Pipeline snapshot (from NativePipelineBridge)
    // ================================================================

    external fun nativeCreateSnapshot(
        input: FloatArray, width: Int, height: Int, channels: Int, paramsArray: FloatArray
    ): Long

    external fun nativeRenderSnapshot(
        snapshotHandle: Long, width: Int, height: Int, channels: Int
    ): FloatArray

    external fun nativeReleaseSnapshot(snapshotHandle: Long)

    fun createSnapshot(input: FloatArray, width: Int, height: Int, channels: Int, paramsArray: FloatArray): Long {
        if (!isAvailable) return 0L
        return NdkSafeCall.execute("createSnapshot") {
            nativeCreateSnapshot(input, width, height, channels, paramsArray)
        } ?: 0L
    }

    fun renderSnapshot(snapshotHandle: Long, width: Int, height: Int, channels: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("renderSnapshot") {
            nativeRenderSnapshot(snapshotHandle, width, height, channels)
        }
    }

    fun releaseSnapshot(snapshotHandle: Long) {
        if (!isAvailable) return
        NdkSafeCall.execute("releaseSnapshot") {
            nativeReleaseSnapshot(snapshotHandle)
        }
    }

    // ================================================================
    // Planckian white balance (from NativePipelineBridge)
    // ================================================================

    external fun nativePlanckianWhiteBalance(
        input: FloatArray, width: Int, height: Int, channels: Int,
        temperature: Float, tint: Float
    ): FloatArray

    external fun nativeGetPlanckianMultipliers(temperature: Float): FloatArray

    fun applyPlanckianWhiteBalance(input: FloatArray, width: Int, height: Int, channels: Int,
                                    temperature: Float, tint: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyPlanckianWhiteBalance") {
            nativePlanckianWhiteBalance(input, width, height, channels, temperature, tint)
        }
    }

    fun getPlanckianMultipliers(temperature: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("getPlanckianMultipliers") {
            nativeGetPlanckianMultipliers(temperature)
        }
    }

    // ================================================================
    // AHD / AMAZE demosaic (from NativePipelineBridge)
    // ================================================================

    external fun nativeDemosaicAHD(
        rawData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int
    ): FloatArray

    external fun nativeDemosaicAMAZE(
        rawData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int
    ): FloatArray

    fun demosaicAHD(rawData: ShortArray, width: Int, height: Int,
                    bayerPattern: Int, whiteLevel: Int, blackLevel: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("demosaicAHD") {
            nativeDemosaicAHD(rawData, width, height, bayerPattern, whiteLevel, blackLevel)
        }
    }

    fun demosaicAMAZE(rawData: ShortArray, width: Int, height: Int,
                      bayerPattern: Int, whiteLevel: Int, blackLevel: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("demosaicAMAZE") {
            nativeDemosaicAMAZE(rawData, width, height, bayerPattern, whiteLevel, blackLevel)
        }
    }

    // ================================================================
    // New pipeline operators (from NativePipelineBridge)
    // ================================================================

    external fun nativeSetColorTemp(
        input: FloatArray, width: Int, height: Int, channels: Int,
        cct: Float, tint: Float, mode: Int
    ): FloatArray

    external fun nativeSetCST(
        input: FloatArray, width: Int, height: Int, channels: Int,
        transformType: Int, inputSpace: String, outputSpace: String
    ): FloatArray

    external fun nativeSetODT(
        input: FloatArray, width: Int, height: Int, channels: Int,
        method: Int, outputSpace: Int, peakLuminance: Float
    ): FloatArray

    external fun nativeLoadLMT(
        input: FloatArray, width: Int, height: Int, channels: Int, lutPath: String
    ): FloatArray

    external fun nativeSetHLS(
        input: FloatArray, width: Int, height: Int, channels: Int,
        profileIndex: Int, hueShift: Float, lightness: Float, saturation: Float, hueRange: Float
    ): FloatArray

    external fun nativeSetCurve(
        input: FloatArray, width: Int, height: Int, channels: Int,
        ctrlPts: FloatArray, count: Int
    ): FloatArray

    external fun nativeSetRawDecode(
        input: FloatArray, width: Int, height: Int, channels: Int,
        demosaicMethod: Int, inputSpace: Int, useCameraWB: Boolean, highlightRecovery: Boolean
    ): FloatArray

    fun applyColorTemp(input: FloatArray, width: Int, height: Int, channels: Int,
                       cct: Float, tint: Float, mode: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyColorTemp") {
            nativeSetColorTemp(input, width, height, channels, cct, tint, mode)
        }
    }

    fun applyCST(input: FloatArray, width: Int, height: Int, channels: Int,
                 transformType: Int, inputSpace: String, outputSpace: String): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyCST") {
            nativeSetCST(input, width, height, channels, transformType, inputSpace, outputSpace)
        }
    }

    fun applyODT(input: FloatArray, width: Int, height: Int, channels: Int,
                 method: Int, outputSpace: Int, peakLuminance: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyODT") {
            nativeSetODT(input, width, height, channels, method, outputSpace, peakLuminance)
        }
    }

    fun applyLMT(input: FloatArray, width: Int, height: Int, channels: Int, lutPath: String): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyLMT") {
            nativeLoadLMT(input, width, height, channels, lutPath)
        }
    }

    fun applyHLS(input: FloatArray, width: Int, height: Int, channels: Int,
                 profileIndex: Int, hueShift: Float, lightness: Float, saturation: Float, hueRange: Float): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyHLS") {
            nativeSetHLS(input, width, height, channels, profileIndex, hueShift, lightness, saturation, hueRange)
        }
    }

    fun applyCurve(input: FloatArray, width: Int, height: Int, channels: Int,
                   ctrlPts: FloatArray, count: Int): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyCurve") {
            nativeSetCurve(input, width, height, channels, ctrlPts, count)
        }
    }

    fun applyRawDecode(input: FloatArray, width: Int, height: Int, channels: Int,
                       demosaicMethod: Int, inputSpace: Int, useCameraWB: Boolean, highlightRecovery: Boolean): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("applyRawDecode") {
            nativeSetRawDecode(input, width, height, channels, demosaicMethod, inputSpace, useCameraWB, highlightRecovery)
        }
    }

    // ================================================================
    // Denoise operators
    // ================================================================

    /** 降噪 — 亮度降噪（基于非局部均值算法） */
    external fun nativeApplyLuminanceDenoise(
        input: FloatArray, width: Int, height: Int, channels: Int,
        strength: Float, detailPreserve: Float
    ): FloatArray

    /** 降噪 — 色彩降噪（基于双边滤波） */
    external fun nativeApplyChromaDenoise(
        input: FloatArray, width: Int, height: Int, channels: Int,
        strength: Float, colorThreshold: Float
    ): FloatArray

    fun applyLuminanceDenoise(
        input: FloatArray, width: Int, height: Int, channels: Int,
        strength: Float, detailPreserve: Float
    ): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.executeFloatArray("applyLuminanceDenoise") {
            nativeApplyLuminanceDenoise(input, width, height, channels, strength, detailPreserve)
        }
    }

    fun applyChromaDenoise(
        input: FloatArray, width: Int, height: Int, channels: Int,
        strength: Float, colorThreshold: Float
    ): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.executeFloatArray("applyChromaDenoise") {
            nativeApplyChromaDenoise(input, width, height, channels, strength, colorThreshold)
        }
    }

    // ================================================================
    // Brush stroke rasterization (画笔笔触光栅化为单通道遮罩)
    // ================================================================

    /**
     * 将画笔笔触光栅化为单通道遮罩 (0-1)。
     *
     * 笔触点序列 [strokePointsX]/[strokePointsY] 使用归一化坐标 (0-1)，
     * 相邻点会连接为线段，沿路径绘制半径为 [brushSize] 的圆盘。
     * [brushHardness] 控制边缘羽化：1.0=锐利边缘，0.0=最柔。
     * [brushOpacity] 控制最终遮罩强度（0-1）。
     *
     * 返回长度为 width*height 的 FloatArray，每个元素为遮罩值 (0-1)。
     */
    external fun nativeRasterizeBrushStrokes(
        width: Int, height: Int,
        strokePointsX: FloatArray, strokePointsY: FloatArray, strokePointsCount: Int,
        brushSize: Float, brushHardness: Float, brushOpacity: Float
    ): FloatArray

    fun rasterizeBrushStrokes(
        width: Int, height: Int,
        strokePointsX: FloatArray, strokePointsY: FloatArray, strokePointsCount: Int,
        brushSize: Float, brushHardness: Float, brushOpacity: Float
    ): FloatArray? {
        if (!isAvailable) return null
        return NdkSafeCall.executeFloatArray("rasterizeBrushStrokes") {
            nativeRasterizeBrushStrokes(
                width, height,
                strokePointsX, strokePointsY, strokePointsCount,
                brushSize, brushHardness, brushOpacity
            )
        }
    }
}