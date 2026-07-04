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

    // ── New operators (ported from desktop) ──

    /** Black level adjustment: map values below black_point to 0. */
    external fun nativeApplyBlackOp(
        input: FloatArray, width: Int, height: Int, channels: Int,
        blackPoint: Float
    ): FloatArray

    /** White level adjustment: map values above white_point to 1. */
    external fun nativeApplyWhiteOp(
        input: FloatArray, width: Int, height: Int, channels: Int,
        whitePoint: Float
    ): FloatArray

    /** Independent shadow adjustment: affects only dark regions (luminance < 0.25). */
    external fun nativeApplyShadowOp(
        input: FloatArray, width: Int, height: Int, channels: Int,
        shadowAmount: Float
    ): FloatArray

    /** Independent highlight adjustment: affects only bright regions (luminance > 0.75). */
    external fun nativeApplyHighlightOp(
        input: FloatArray, width: Int, height: Int, channels: Int,
        highlightAmount: Float
    ): FloatArray

    /** Crop a region [left, top, right, bottom) from the image. */
    external fun nativeApplyCrop(
        input: FloatArray, width: Int, height: Int, channels: Int,
        left: Int, top: Int, right: Int, bottom: Int
    ): FloatArray

    /** Rotate image clockwise by angle (90, 180, 270). */
    external fun nativeApplyRotate(
        input: FloatArray, width: Int, height: Int, channels: Int,
        angle: Int
    ): FloatArray

    /** Resize image using nearest (0) or bilinear (1) interpolation. */
    external fun nativeApplyResize(
        input: FloatArray, width: Int, height: Int, channels: Int,
        dstWidth: Int, dstHeight: Int, method: Int
    ): FloatArray

    // ── Auto Exposure ──

    /** Compute auto exposure value (EV) for the given image. Returns exposure in stops. */
    external fun nativeComputeAutoExposure(
        input: FloatArray, width: Int, height: Int, channels: Int,
        targetPercentile: Float, targetLuminance: Float
    ): Float

    /** Apply auto exposure adjustment to the image in-place. */
    external fun nativeApplyAutoExposure(
        input: FloatArray, width: Int, height: Int, channels: Int,
        targetPercentile: Float, targetLuminance: Float
    ): FloatArray

    // ── Pipeline Snapshot ──

    /** Create a read-only snapshot of the pipeline state. Returns a handle (0 = failure). */
    external fun nativeCreateSnapshot(
        input: FloatArray, width: Int, height: Int, channels: Int,
        paramsArray: FloatArray
    ): Long

    /** Render a snapshot to a float array. Returns null on failure. */
    external fun nativeRenderSnapshot(
        snapshotHandle: Long, width: Int, height: Int, channels: Int
    ): FloatArray

    /** Release a snapshot and free its memory. */
    external fun nativeReleaseSnapshot(snapshotHandle: Long)

    // ── Planckian Locus Color Temperature ──

    /** Apply Planckian locus white balance (physically accurate color temperature). */
    external fun nativePlanckianWhiteBalance(
        input: FloatArray, width: Int, height: Int, channels: Int,
        temperature: Float, tint: Float
    ): FloatArray

    /** Get the Planckian locus RGB multipliers for a given color temperature. */
    external fun nativeGetPlanckianMultipliers(temperature: Float): FloatArray

    // ── AHD / AMAZE Demosaic ──

    /** AHD (Adaptive Homogeneity-Directed) demosaic algorithm. */
    external fun nativeDemosaicAHD(
        rawData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int
    ): FloatArray

    /** AMAZE (Aliasing Minimization and Zipper Elimination) demosaic algorithm. */
    external fun nativeDemosaicAMAZE(
        rawData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int
    ): FloatArray

    // ── New Pipeline Operators: ColorTemp / CST / ODT / LMT / HLS / Curve / RawDecode ──

    /** Color temperature adjustment using Planckian locus. mode: 0=AS_SHOT, 1=CUSTOM. */
    external fun nativeSetColorTemp(
        input: FloatArray, width: Int, height: Int, channels: Int,
        cct: Float, tint: Float, mode: Int
    ): FloatArray

    /** Color Space Transform (CST). transformType: 0=TO_WORKING_SPACE, 1=TO_OUTPUT_SPACE. */
    external fun nativeSetCST(
        input: FloatArray, width: Int, height: Int, channels: Int,
        transformType: Int, inputSpace: String, outputSpace: String
    ): FloatArray

    /** Output Device Transform (ODT). method: 0=ACES, 1=OPEN_DRT; outputSpace: 0=sRGB, 1=P3, 2=Rec2020. */
    external fun nativeSetODT(
        input: FloatArray, width: Int, height: Int, channels: Int,
        method: Int, outputSpace: Int, peakLuminance: Float
    ): FloatArray

    /** Look Modify Transform (LMT) - apply a .cube LUT file. */
    external fun nativeLoadLMT(
        input: FloatArray, width: Int, height: Int, channels: Int,
        lutPath: String
    ): FloatArray

    /** HLS per-profile color adjustment. profileIndex: 0-7 (8 hue profiles). */
    external fun nativeSetHLS(
        input: FloatArray, width: Int, height: Int, channels: Int,
        profileIndex: Int, hueShift: Float, lightness: Float, saturation: Float,
        hueRange: Float
    ): FloatArray

    /** Hermite monotone spline curve adjustment. ctrlPts is flat [x0,y0,x1,y1,...]. */
    external fun nativeSetCurve(
        input: FloatArray, width: Int, height: Int, channels: Int,
        ctrlPts: FloatArray, count: Int
    ): FloatArray

    /** RAW decode control operator. demosaicMethod: 0=AHD, 1=AMAZE, 2=RCD; inputSpace: 0=AP0, 1=CAMERA. */
    external fun nativeSetRawDecode(
        input: FloatArray, width: Int, height: Int, channels: Int,
        demosaicMethod: Int, inputSpace: Int, useCameraWB: Boolean,
        highlightRecovery: Boolean
    ): FloatArray

    companion object {
        private var libraryLoaded = false

        fun ensureLibraryLoaded() {
            if (!libraryLoaded) {
                try {
                    System.loadLibrary("alcedo")
                    libraryLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    android.util.Log.e("NativePipelineBridge", "Failed to load native library", e)
                }
            }
        }

        init {
            ensureLibraryLoaded()
        }
    }
}