package com.alcedo.studio.ndk

import android.util.Log

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

    external fun nativeDecodeRaw(
        rawData: ShortArray, rawWidth: Int, rawHeight: Int,
        outputRgb: FloatArray, outputWidth: Int, outputHeight: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int,
        highlightReconstruction: Boolean, demosaicAlgorithm: Int
    ): Boolean

    external fun nativeExtractMetadata(filePath: String): String
    external fun nativeGenerateThumbnail(filePath: String, maxWidth: Int, maxHeight: Int): ByteArray?

    fun decodeRaw(
        rawData: ShortArray, rawWidth: Int, rawHeight: Int,
        outputRgb: FloatArray, outputWidth: Int, outputHeight: Int,
        bayerPattern: Int = 0, whiteLevel: Int = 16384, blackLevel: Int = 0,
        highlightReconstruction: Boolean = true, demosaicAlgorithm: Int = 0
    ): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("decodeRaw") {
            nativeDecodeRaw(
                rawData, rawWidth, rawHeight,
                outputRgb, outputWidth, outputHeight,
                bayerPattern, whiteLevel, blackLevel,
                highlightReconstruction, demosaicAlgorithm
            )
        }
    }

    fun extractMetadata(filePath: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("extractMetadata") {
            nativeExtractMetadata(filePath)
        }
    }

    fun generateThumbnail(filePath: String, maxWidth: Int, maxHeight: Int): ByteArray? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("generateThumbnail") {
            nativeGenerateThumbnail(filePath, maxWidth, maxHeight)
        }
    }
}
