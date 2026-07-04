package com.alcedo.studio.domain.service

class NativePipelineBridge {
    // Legacy integer pipeline
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

    // Full float32 pipeline with all parameters
    external fun nativeApplyPipelineFloat(
        input: FloatArray,
        width: Int,
        height: Int,
        channels: Int,
        paramsArray: FloatArray
    ): FloatArray

    // RAW decode
    external fun nativeDecodeRaw(
        rawPath: String,
        demosaic: Int,
        highlightReconstruction: Boolean
    ): IntArray

    // RAW decode float
    external fun nativeDecodeRawFloat(
        rawData: ShortArray,
        rawWidth: Int,
        rawHeight: Int,
        bayerPattern: Int,
        whiteLevel: Int,
        blackLevel: Int,
        highlightReconstruction: Boolean
    ): FloatArray

    // Color science transforms
    external fun nativeApplyAcesTransform(
        input: FloatArray,
        width: Int,
        height: Int,
        peakLuminance: Float
    ): FloatArray

    external fun nativeApplyOpenDRTTransform(
        input: FloatArray,
        width: Int,
        height: Int,
        peakLuminance: Float
    ): FloatArray

    external fun nativeConvertColorSpace(
        input: FloatArray,
        width: Int,
        height: Int,
        srcSpace: Int,
        dstSpace: Int
    ): FloatArray

    external fun nativeApplyEOTF(
        input: FloatArray,
        width: Int,
        height: Int,
        eotfType: Int,
        peakLuminance: Float
    ): FloatArray

    // LUT
    external fun nativeApplyLut(
        pixels: FloatArray,
        width: Int,
        height: Int,
        lutPath: String
    ): Boolean

    // Metadata
    external fun nativeExtractMetadata(
        filePath: String
    ): String

    // OKLab
    external fun nativeSrgbToOklab(
        r: Float, g: Float, b: Float
    ): FloatArray

    external fun nativeOklabToSrgb(
        L: Float, a: Float, bb: Float
    ): FloatArray

    // Sigmoid contrast
    external fun nativeApplySigmoidContrast(
        input: FloatArray,
        width: Int,
        height: Int,
        contrast: Float,
        pivot: Float,
        shoulder: Float
    ): FloatArray

    companion object {
        init {
            System.loadLibrary("alcedo_core")
        }
    }
}