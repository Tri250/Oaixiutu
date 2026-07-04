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
        return floatArrayOf(
            // Basic adjustments
            params.exposure,
            params.contrast,
            params.saturation,
            params.vibrance,
            params.highlights,
            params.shadows,
            params.midtones,
            params.whiteBalanceTemp,
            params.whiteBalanceTint,
            params.sharpenAmount,
            params.clarityAmount,
            params.clarityRadius,
            params.filmGrainIntensity,
            params.halationIntensity,
            params.halationThreshold,
            params.halationSpread,
            params.halationRedBias,
            params.sigmoidContrast,

            // Color wheels
            params.colorWheelLiftR,
            params.colorWheelLiftG,
            params.colorWheelLiftB,
            params.colorWheelGammaR,
            params.colorWheelGammaG,
            params.colorWheelGammaB,
            params.colorWheelGainR,
            params.colorWheelGainG,
            params.colorWheelGainB,

            // Tint
            params.tintHighlightHue,
            params.tintHighlightStrength,
            params.tintShadowHue,
            params.tintShadowStrength,
            params.tintBalance,

            // Display transform
            params.displayTransform.colorScience.ordinal.toFloat(),
            params.displayTransform.eotf.ordinal.toFloat(),
            params.displayTransform.peakLuminance,
            params.displayTransform.displayColorSpace.ordinal.toFloat()
        )
    }
}