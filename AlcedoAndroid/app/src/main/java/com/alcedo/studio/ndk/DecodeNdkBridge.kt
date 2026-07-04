package com.alcedo.studio.ndk

import android.util.Log
import com.alcedo.studio.domain.service.NativeDecodeResult
import com.alcedo.studio.domain.service.NativeRawInfoResult
import com.alcedo.studio.domain.service.NativeThumbnailResult

object DecodeNdkBridge {
    private const val TAG = "DecodeNdkBridge"

    var isAvailable = false
        private set

    init {
        try {
            System.loadLibrary("alcedo")
            isAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            isAvailable = false
        }
    }

    // Format detection
    external fun nativeDetectFormat(filePath: String): String
    external fun nativeIsRawFormat(filePath: String): Boolean

    // RAW image info
    external fun nativeReadRawInfo(filePath: String): NativeRawInfoResult?

    // Full RAW decode
    external fun nativeDecodeRaw(
        filePath: String, demosaic: Int, highlightReconstruction: Boolean,
        useCameraMatrix: Boolean, halfResolution: Boolean, outputFloat: Boolean,
        extractThumbnail: Boolean, extractPreview: Boolean, maxThumbnailDim: Int,
        wbIlluminant: Int
    ): NativeDecodeResult?

    // RAW decode from memory
    external fun nativeDecodeRawFromMemory(
        data: ByteArray, demosaic: Int, highlightReconstruction: Boolean,
        useCameraMatrix: Boolean, halfResolution: Boolean, outputFloat: Boolean
    ): NativeDecodeResult?

    // Metadata extraction
    external fun nativeExtractMetadata(filePath: String): String?
    external fun nativeExtractMetadataFromMemory(data: ByteArray): String?
    external fun nativeExtractExif(filePath: String): String?
    external fun nativeExtractXmp(filePath: String): String?
    external fun nativeExtractIccProfile(filePath: String): ByteArray?
    external fun nativeExtractDngColor(filePath: String): FloatArray?

    // Thumbnail generation
    external fun nativeGenerateThumbnail(filePath: String, maxDimension: Int, useEmbedded: Boolean): NativeThumbnailResult?
    external fun nativeGenerateThumbnailFromRGB(rgbData: FloatArray, width: Int, height: Int, maxDimension: Int): ByteArray?
    external fun nativeExtractEmbeddedThumbnail(filePath: String): ByteArray?
    external fun nativeExtractEmbeddedPreview(filePath: String): ByteArray?

    // Demosaic
    external fun nativeDemosaic(
        rawCfaData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int, demosaicMethod: Int
    ): FloatArray?

    // White balance / Color matrix / Highlight reconstruction / Black level
    external fun nativeApplyWhiteBalance(rgbData: FloatArray, width: Int, height: Int, rMultiplier: Float, gMultiplier: Float, bMultiplier: Float)
    external fun nativeApplyColorMatrix(rgbData: FloatArray, width: Int, height: Int, matrix: FloatArray)
    external fun nativeReconstructHighlights(rgbData: FloatArray, width: Int, height: Int, whiteLevel: Int, mode: Int)
    external fun nativeSubtractBlackLevel(rawData: ShortArray, width: Int, height: Int, blackLevelR: Int, blackLevelG1: Int, blackLevelG2: Int, blackLevelB: Int)

    // Cancellation & Scheduler
    external fun nativeCancelDecode(jobId: Long)
    external fun nativeCancelAllDecodes()
    external fun nativeInitScheduler(threadCount: Int)
    external fun nativeShutdownScheduler()
    external fun nativeSetProgressCallback(callback: Any?)

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

    fun extractMetadata(filePath: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractMetadata") {
            nativeExtractMetadata(filePath)
        }
    }

    fun generateThumbnail(filePath: String, maxDimension: Int, useEmbedded: Boolean): NativeThumbnailResult? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("generateThumbnail") {
            nativeGenerateThumbnail(filePath, maxDimension, useEmbedded)
        }
    }

    fun cancelDecode(jobId: Long) {
        if (!isAvailable) return
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
}
