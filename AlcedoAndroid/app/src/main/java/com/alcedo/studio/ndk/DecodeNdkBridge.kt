package com.alcedo.studio.ndk

object DecodeNdkBridge {
    init {
        System.loadLibrary("alcedo")
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
        return nativeDecodeRaw(
            rawData, rawWidth, rawHeight,
            outputRgb, outputWidth, outputHeight,
            bayerPattern, whiteLevel, blackLevel,
            highlightReconstruction, demosaicAlgorithm
        )
    }
}
