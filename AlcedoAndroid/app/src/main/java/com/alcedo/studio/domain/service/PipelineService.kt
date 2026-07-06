package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PipelineService {

    private val nativeBridge = NativePipelineBridge()

    // ================================================================
    // Main pipeline entry point
    // ================================================================

    suspend fun applyPipeline(bitmap: Bitmap, params: PipelineParams): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height

        // Convert Bitmap to float array
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val floatPixels = FloatArray(pixelCount * 4)
        for (i in 0 until pixelCount) {
            val pixel = pixels[i]
            floatPixels[i * 4]     = ((pixel shr 16) and 0xFF) / 255.0f
            floatPixels[i * 4 + 1] = ((pixel shr 8) and 0xFF) / 255.0f
            floatPixels[i * 4 + 2] = (pixel and 0xFF) / 255.0f
            floatPixels[i * 4 + 3] = ((pixel shr 24) and 0xFF) / 255.0f
        }

        // Build params array for native pipeline
        val paramsArray = buildParamsArray(params)

        // Run native pipeline
        val result = nativeBridge.nativeApplyPipelineFloat(floatPixels, width, height, 4, paramsArray)

        // Convert back to Bitmap
        val resultPixels = IntArray(pixelCount)
        for (i in 0 until pixelCount) {
            val a = (result[i * 4 + 3].coerceIn(0f, 1f) * 255f).toInt()
            val r = (result[i * 4].coerceIn(0f, 1f) * 255f).toInt()
            val g = (result[i * 4 + 1].coerceIn(0f, 1f) * 255f).toInt()
            val b = (result[i * 4 + 2].coerceIn(0f, 1f) * 255f).toInt()
            resultPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
        resultBitmap
    }

    // ================================================================
    // Individual stage operations
    // ================================================================

    suspend fun applyAcesTransform(bitmap: Bitmap, peakLuminance: Float = 100f): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = bitmapToFloatArray(bitmap)

        val result = nativeBridge.nativeApplyAcesTransform(pixels, width, height, peakLuminance)
        floatArrayToBitmap(result, width, height)
    }

    suspend fun applyOpenDRTTransform(bitmap: Bitmap, peakLuminance: Float = 100f): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = bitmapToFloatArray(bitmap)

        val result = nativeBridge.nativeApplyOpenDRTTransform(pixels, width, height, peakLuminance)
        floatArrayToBitmap(result, width, height)
    }

    suspend fun applyEOTF(bitmap: Bitmap, eotfType: Int, peakLuminance: Float = 100f): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = bitmapToFloatArray(bitmap)

        val result = nativeBridge.nativeApplyEOTF(pixels, width, height, eotfType, peakLuminance)
        floatArrayToBitmap(result, width, height)
    }

    suspend fun applySigmoidContrast(bitmap: Bitmap, contrast: Float, pivot: Float = 0.18f, shoulder: Float = 0.5f): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = bitmapToFloatArray(bitmap)

        val result = nativeBridge.nativeApplySigmoidContrast(pixels, width, height, contrast, pivot, shoulder)
        floatArrayToBitmap(result, width, height)
    }

    suspend fun applyLut(bitmap: Bitmap, lutPath: String): Boolean = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = bitmapToFloatArray(bitmap)
        nativeBridge.nativeApplyLut(pixels, width, height, lutPath)
    }

    // ================================================================
    // Color science
    // ================================================================

    fun srgbToOklab(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val result = nativeBridge.nativeSrgbToOklab(r, g, b)
        return Triple(result[0], result[1], result[2])
    }

    fun oklabToSrgb(L: Float, a: Float, bb: Float): Triple<Float, Float, Float> {
        val result = nativeBridge.nativeOklabToSrgb(L, a, bb)
        return Triple(result[0], result[1], result[2])
    }

    // ================================================================
    // Metadata extraction
    // ================================================================

    fun extractMetadata(path: String): String {
        return nativeBridge.nativeExtractMetadata(path)
    }

    // ================================================================
    // Auto Exposure
    // ================================================================

    /**
     * Compute auto exposure value (EV) for the given bitmap.
     * Uses percentile-based approach targeting middle-gray (18%) at 50th percentile.
     * Returns exposure adjustment in stops (EV).
     */
    fun computeAutoExposure(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        targetPercentile: Float = 0.5f,
        targetLuminance: Float = 0.18f
    ): Float {
        return nativeBridge.nativeComputeAutoExposure(
            floatPixels, width, height, channels, targetPercentile, targetLuminance)
    }

    /**
     * Apply auto exposure adjustment to float pixel data.
     */
    fun applyAutoExposure(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        targetPercentile: Float = 0.5f,
        targetLuminance: Float = 0.18f
    ): FloatArray {
        return nativeBridge.nativeApplyAutoExposure(
            floatPixels, width, height, channels, targetPercentile, targetLuminance)
    }

    // ================================================================
    // Pipeline Snapshot
    // ================================================================

    private var activeSnapshotHandle: Long = 0

    /**
     * Create a read-only snapshot of the current pipeline state.
     * Can be used for background analysis rendering without interfering
     * with the active pipeline.
     */
    fun createSnapshot(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        params: PipelineParams
    ): Long {
        val paramsArray = buildParamsArray(params)
        val handle = nativeBridge.nativeCreateSnapshot(floatPixels, width, height, channels, paramsArray)
        activeSnapshotHandle = handle
        return handle
    }

    /**
     * Render a pipeline snapshot to a float array.
     */
    fun renderSnapshot(handle: Long, width: Int, height: Int, channels: Int): FloatArray? {
        return nativeBridge.nativeRenderSnapshot(handle, width, height, channels)
    }

    /**
     * Release a pipeline snapshot and free its memory.
     */
    fun releaseSnapshot(handle: Long) {
        nativeBridge.nativeReleaseSnapshot(handle)
        if (activeSnapshotHandle == handle) {
            activeSnapshotHandle = 0
        }
    }

    /**
     * Release the active snapshot if any.
     */
    fun releaseActiveSnapshot() {
        if (activeSnapshotHandle != 0L) {
            releaseSnapshot(activeSnapshotHandle)
        }
    }

    // ================================================================
    // Planckian Locus White Balance
    // ================================================================

    /**
     * Apply Planckian locus white balance for physically accurate color temperature.
     */
    fun applyPlanckianWhiteBalance(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        temperature: Float, tint: Float
    ): FloatArray {
        return nativeBridge.nativePlanckianWhiteBalance(
            floatPixels, width, height, channels, temperature, tint)
    }

    /**
     * Get the Planckian locus RGB multipliers for a given color temperature.
     * Returns [r_mult, g_mult, b_mult].
     */
    fun getPlanckianMultipliers(temperature: Float): FloatArray {
        return nativeBridge.nativeGetPlanckianMultipliers(temperature)
    }

    // ================================================================
    // AHD / AMAZE Demosaic
    // ================================================================

    /**
     * AHD (Adaptive Homogeneity-Directed) demosaic algorithm.
     * Higher quality than RCD, reduces color artifacts and zipper effects.
     */
    fun demosaicAHD(
        rawData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int
    ): FloatArray {
        return nativeBridge.nativeDemosaicAHD(rawData, width, height, bayerPattern, whiteLevel, blackLevel)
    }

    /**
     * AMAZE (Aliasing Minimization and Zipper Elimination) demosaic algorithm.
     * Popular high-quality demosaic known for excellent detail preservation.
     */
    fun demosaicAMAZE(
        rawData: ShortArray, width: Int, height: Int,
        bayerPattern: Int, whiteLevel: Int, blackLevel: Int
    ): FloatArray {
        return nativeBridge.nativeDemosaicAMAZE(rawData, width, height, bayerPattern, whiteLevel, blackLevel)
    }

    // ================================================================
    // New Pipeline Operators: ColorTemp / CST / ODT / LMT / HLS / Curve / RawDecode
    // ================================================================

    /**
     * Apply color temperature adjustment using Planckian locus lookup.
     * @param cct Correlated color temperature in Kelvin (2000–15000).
     * @param tint Green-magenta tint offset (±150).
     * @param mode 0=AS_SHOT (use camera metadata), 1=CUSTOM.
     */
    suspend fun applyColorTemp(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        cct: Float, tint: Float, mode: Int = 1
    ): FloatArray = withContext(Dispatchers.Default) {
        nativeBridge.nativeSetColorTemp(floatPixels, width, height, channels, cct, tint, mode)
    }

    /**
     * Apply Color Space Transform (CST) between named color spaces.
     * @param transformType 0=TO_WORKING_SPACE, 1=TO_OUTPUT_SPACE.
     * @param inputSpace Source color space name (e.g. "sRGB", "ACES AP1", "ACES AP0", "Display P3", "Rec2020").
     * @param outputSpace Target color space name.
     */
    suspend fun applyCST(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        transformType: Int, inputSpace: String, outputSpace: String
    ): FloatArray = withContext(Dispatchers.Default) {
        nativeBridge.nativeSetCST(floatPixels, width, height, channels, transformType, inputSpace, outputSpace)
    }

    /**
     * Apply Output Device Transform (ODT) for display rendering.
     * @param method 0=ACES (RRT+ODT), 1=OPEN_DRT.
     * @param outputSpace 0=sRGB, 1=Display P3, 2=Rec2020.
     * @param peakLuminance Display peak luminance in nits (e.g. 100.0f for SDR).
     */
    suspend fun applyODT(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        method: Int, outputSpace: Int = 0, peakLuminance: Float = 100f
    ): FloatArray = withContext(Dispatchers.Default) {
        nativeBridge.nativeSetODT(floatPixels, width, height, channels, method, outputSpace, peakLuminance)
    }

    /**
     * Apply Look Modify Transform (LMT) using a .cube LUT file.
     * @param lutPath Absolute path to the .cube LUT file.
     */
    suspend fun applyLMT(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        lutPath: String
    ): FloatArray = withContext(Dispatchers.Default) {
        nativeBridge.nativeLoadLMT(floatPixels, width, height, channels, lutPath)
    }

    /**
     * Apply HLS (Hue-Lightness-Saturation) per-profile color adjustment.
     * Uses 8 hue profiles centered at 0°, 45°, 90°, …, 315°.
     * @param profileIndex Hue profile index (0–7).
     * @param hueShift Hue rotation in degrees (±180).
     * @param lightness Lightness offset (±1.0).
     * @param saturation Saturation scale offset (±1.0).
     * @param hueRange Half-width of the hue selection range in degrees (1–180).
     */
    suspend fun applyHLS(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        profileIndex: Int, hueShift: Float, lightness: Float, saturation: Float,
        hueRange: Float = 45f
    ): FloatArray = withContext(Dispatchers.Default) {
        nativeBridge.nativeSetHLS(floatPixels, width, height, channels, profileIndex, hueShift, lightness, saturation, hueRange)
    }

    /**
     * Apply Hermite monotone spline curve adjustment to luminance.
     * @param ctrlPts Flat control point array [x0,y0,x1,y1,…] with x,y in [0,1].
     * @param count Number of control points.
     */
    suspend fun applyCurve(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        ctrlPts: FloatArray, count: Int
    ): FloatArray = withContext(Dispatchers.Default) {
        nativeBridge.nativeSetCurve(floatPixels, width, height, channels, ctrlPts, count)
    }

    /**
     * Apply RAW decode control operator.
     * Configures demosaic method, input color space, and white balance/highlight recovery preferences.
     * @param demosaicMethod 0=AHD, 1=AMAZE, 2=RCD.
     * @param inputSpace 0=AP0, 1=CAMERA (native camera space, CST will convert later).
     * @param useCameraWB Use camera-recorded white balance multipliers.
     * @param highlightRecovery Enable highlight reconstruction.
     */
    suspend fun applyRawDecode(
        floatPixels: FloatArray, width: Int, height: Int, channels: Int,
        demosaicMethod: Int, inputSpace: Int = 0, useCameraWB: Boolean = true,
        highlightRecovery: Boolean = true
    ): FloatArray = withContext(Dispatchers.Default) {
        nativeBridge.nativeSetRawDecode(floatPixels, width, height, channels, demosaicMethod, inputSpace, useCameraWB, highlightRecovery)
    }

    // ================================================================
    // Helpers
    // ================================================================

    private fun bitmapToFloatArray(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val floatPixels = FloatArray(pixelCount * 3)
        for (i in 0 until pixelCount) {
            val pixel = pixels[i]
            floatPixels[i * 3]     = ((pixel shr 16) and 0xFF) / 255.0f
            floatPixels[i * 3 + 1] = ((pixel shr 8) and 0xFF) / 255.0f
            floatPixels[i * 3 + 2] = (pixel and 0xFF) / 255.0f
        }
        return floatPixels
    }

    private fun floatArrayToBitmap(floatPixels: FloatArray, width: Int, height: Int): Bitmap {
        val pixelCount = width * height
        val channels = floatPixels.size / pixelCount
        val resultPixels = IntArray(pixelCount)

        for (i in 0 until pixelCount) {
            val idx = i * channels
            val r = (floatPixels[idx].coerceIn(0f, 1f) * 255f).toInt()
            val g = (floatPixels[idx + 1].coerceIn(0f, 1f) * 255f).toInt()
            val b = (floatPixels[idx + 2].coerceIn(0f, 1f) * 255f).toInt()
            val a = if (channels >= 4) (floatPixels[idx + 3].coerceIn(0f, 1f) * 255f).toInt() else 255
            resultPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun buildParamsArray(params: PipelineParams): FloatArray {
        // C1 修复: 完整序列化所有 PipelineParams 字段到原生管线,
        // 确保 HSL/色调曲线/通道混合器/裁剪/镜头校正/自动曝光等
        // 用户调整真正传递到原生 process(),否则预览不响应这些操作。
        // 顺序必须与 native-lib.cpp 中 SAFE_PARAM 读取顺序严格一致。
        val list = mutableListOf<Float>()

        // ── Basic adjustments (idx 0-17) ──
        list += params.exposure
        list += params.contrast
        list += params.saturation
        list += params.vibrance
        list += params.highlights
        list += params.shadows
        list += params.midtones
        list += params.whiteBalanceTemp
        list += params.whiteBalanceTint
        list += params.sharpenAmount
        list += params.clarityAmount
        list += params.clarityRadius
        list += params.filmGrainIntensity
        list += params.halationIntensity
        list += params.halationThreshold
        list += params.halationSpread
        list += params.halationRedBias
        list += params.sigmoidContrast

        // ── Color wheels (idx 18-26) ──
        list += params.colorWheelLiftR
        list += params.colorWheelLiftG
        list += params.colorWheelLiftB
        list += params.colorWheelGammaR
        list += params.colorWheelGammaG
        list += params.colorWheelGammaB
        list += params.colorWheelGainR
        list += params.colorWheelGainG
        list += params.colorWheelGainB

        // ── Tint (idx 27-31) ──
        list += params.tintHighlightHue
        list += params.tintHighlightStrength
        list += params.tintShadowHue
        list += params.tintShadowStrength
        list += params.tintBalance

        // ── Display transform (idx 32-35) ──
        list += params.displayTransform.colorScience.ordinal.toFloat()
        list += params.displayTransform.eotf.ordinal.toFloat()
        list += params.displayTransform.peakLuminance
        list += params.displayTransform.displayColorSpace.ordinal.toFloat()

        // ── Tone region boundaries (idx 36-37) ──
        list += params.shadowBoundary
        list += params.highlightBoundary

        // ── Auto exposure (idx 38-41) ──
        list += if (params.autoExposureEnabled) 1f else 0f
        list += params.autoExposureTargetPercentile
        list += params.autoExposureTargetLuminance
        list += params.autoExposureValue

        // ── Tone curve (idx 42-75): points count + x[16] + y[16] ──
        list += params.toneCurvePoints.toFloat()
        // Pad tone curve arrays to 16 entries (C++ struct fixed size)
        for (i in 0 until 16) {
            list += if (i < params.toneCurveX.size) params.toneCurveX[i] else 0f
        }
        for (i in 0 until 16) {
            list += if (i < params.toneCurveY.size) params.toneCurveY[i] else 0f
        }

        // ── HSL (idx 76-99): hue_shift[8] + sat_scale[8] + lum_scale[8] ──
        for (i in 0 until 8) {
            list += if (i < params.hslHueShift.size) params.hslHueShift[i] else 0f
        }
        for (i in 0 until 8) {
            list += if (i < params.hslSaturationScale.size) params.hslSaturationScale[i] else 1f
        }
        for (i in 0 until 8) {
            list += if (i < params.hslLuminanceScale.size) params.hslLuminanceScale[i] else 1f
        }

        // ── Channel mixer (idx 100-109): matrix[9] + monochrome ──
        for (i in 0 until 9) {
            list += if (i < params.channelMixerMatrix.size) params.channelMixerMatrix[i] else 0f
        }
        list += if (params.channelMixerMonochrome) 1f else 0f

        // ── Crop (idx 110-113) ──
        list += params.geometryCropLeft
        list += params.geometryCropTop
        list += params.geometryCropRight
        list += params.geometryCropBottom

        // ── Lens correction (idx 114-118) ──
        list += params.lensK1
        list += params.lensK2
        list += params.lensK3
        list += params.lensP1
        list += params.lensP2

        // ── LUT enable flag (idx 119); lutPath is a string, applied via separate native call ──
        list += if (params.lutEnabled) 1f else 0f

        return list.toFloatArray()
    }
}