package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.alcedo.studio.data.model.BrushPoint
import com.alcedo.studio.data.model.MaskCombineMode
import com.alcedo.studio.data.model.MaskParams
import com.alcedo.studio.data.model.MaskType
import com.alcedo.studio.data.model.SubMask
import com.alcedo.studio.ndk.AlcedoNativeBridge
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mask generation and application service backed by the native MaskOperator.
 *
 * Provides high-level Kotlin APIs that map [MaskType] / [MaskParams] to the
 * native mask_op JNI bridge, and can combine / feather / invert masks
 * through either the native path (for geometric / brush masks) or the
 * existing [MaskInferenceService] (for AI masks).
 */
class MaskService(
    private val inferenceService: MaskInferenceService = MaskInferenceService()
) {
    companion object {
        private const val TAG = "MaskService"

        // Native mask type codes matching C++ MaskType enum
        const val NATIVE_MASK_BRUSH = 0
        const val NATIVE_MASK_LINEAR = 1
        const val NATIVE_MASK_RADIAL = 2
        const val NATIVE_MASK_LUMINOSITY = 3
        const val NATIVE_MASK_COLOR_RANGE = 4
        const val NATIVE_MASK_WHOLE_IMAGE = 5

        // Native combine mode codes matching C++ CombineMode enum
        const val NATIVE_COMBINE_ADD = 0
        const val NATIVE_COMBINE_SUBTRACT = 1
        const val NATIVE_COMBINE_INTERSECT = 2
    }

    /**
     * Generate a single sub-mask as a FloatArray of weights [0..1].
     * Falls back to [MaskInferenceService.rasterize] for types not
     * supported by the native operator (SUBJECT, SKY, FOREGROUND).
     */
    suspend fun generateSubMask(
        subMask: SubMask,
        bitmap: Bitmap
    ): FloatArray {
        val w = bitmap.width
        val h = bitmap.height

        // AI masks go through the inference service
        return when (subMask.type) {
            MaskType.SUBJECT, MaskType.SKY, MaskType.FOREGROUND -> {
                val result = inferenceService.infer(bitmap, subMask.type)
                result.weights
            }
            else -> generateNativeMask(w, h, subMask.type, subMask.params)
        }
    }

    /**
     * Generate a mask via the native MaskOperator.
     */
    fun generateNativeMask(
        width: Int, height: Int,
        type: MaskType, params: MaskParams
    ): FloatArray {
        val nativeType = maskTypeToNative(type)
        val maskParamsArray = paramsToNativeArray(params)

        // Brush points (only for brush type)
        val brushPts = params.brushPoints ?: emptyList()
        val ptsX = FloatArray(brushPts.size) { brushPts[it].x }
        val ptsY = FloatArray(brushPts.size) { brushPts[it].y }
        val pressures = FloatArray(brushPts.size) { brushPts[it].pressure }

        val result = AlcedoNativeBridge.generateMask(
            width, height, nativeType, maskParamsArray,
            ptsX, ptsY, pressures, brushPts.size
        )

        if (result != null && result.size == width * height) {
            return result
        }

        // Fallback: generate mask in Kotlin if native fails
        Log.w(TAG, "Native mask generation failed or unavailable, using Kotlin fallback")
        return generateKotlinFallback(width, height, type, params)
    }

    /**
     * Apply a mask to blend original and edited pixel arrays.
     * Both arrays must be float RGBA (width * height * 4).
     */
    fun applyMask(
        original: FloatArray, edited: FloatArray, mask: FloatArray,
        width: Int, height: Int, channels: Int = 4
    ): FloatArray {
        val result = AlcedoNativeBridge.applyMask(original, edited, mask, width, height, channels)
        if (result != null) return result

        // Kotlin fallback
        val output = FloatArray(original.size)
        for (i in 0 until width * height) {
            val w = mask[i].coerceIn(0f, 1f)
            val invW = 1f - w
            val idx = i * channels
            for (c in 0 until channels) {
                output[idx + c] = original[idx + c] * invW + edited[idx + c] * w
            }
        }
        return output
    }

    /**
     * Combine two mask weight arrays.
     */
    fun combineMasks(
        maskA: FloatArray, maskB: FloatArray,
        width: Int, height: Int, mode: MaskCombineMode
    ): FloatArray {
        val nativeMode = when (mode) {
            MaskCombineMode.ADDITIVE -> NATIVE_COMBINE_ADD
            MaskCombineMode.SUBTRACTIVE -> NATIVE_COMBINE_SUBTRACT
            MaskCombineMode.INTERSECT -> NATIVE_COMBINE_INTERSECT
        }

        val result = AlcedoNativeBridge.combineMasks(maskA, maskB, width, height, nativeMode)
        if (result != null) return result

        // Kotlin fallback
        val n = width * height
        return FloatArray(n) { i ->
            when (mode) {
                MaskCombineMode.ADDITIVE -> min(1f, maskA[i] + maskB[i])
                MaskCombineMode.SUBTRACTIVE -> (maskA[i] - maskB[i]).coerceIn(0f, 1f)
                MaskCombineMode.INTERSECT -> min(maskA[i], maskB[i])
            }
        }
    }

    /**
     * Feather (blur) a mask with the given pixel radius.
     */
    fun featherMask(mask: FloatArray, width: Int, height: Int, radiusPx: Float): FloatArray {
        val result = AlcedoNativeBridge.featherMask(mask, width, height, radiusPx)
        if (result != null) return result

        // Kotlin fallback: simple box blur
        return kotlinBoxBlur(mask, width, height, radiusPx.toInt().coerceAtLeast(1))
    }

    /**
     * Invert a mask (1 - mask[i]).
     */
    fun invertMask(mask: FloatArray): FloatArray {
        return FloatArray(mask.size) { 1f - mask[it] }
    }

    /**
     * Build the combined mask for a container by merging its sub-masks.
     */
    suspend fun renderContainerMask(
        subMasks: List<SubMask>,
        bitmap: Bitmap
    ): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        var accumulated = FloatArray(w * h)
        var started = false

        for (sub in subMasks) {
            if (!sub.visible) continue
            val subMaskData = generateSubMask(sub, bitmap)

            // Apply sub-mask opacity and inversion
            val contribution = FloatArray(w * h)
            for (i in contribution.indices) {
                var wv = subMaskData[i] * sub.opacity.coerceIn(0f, 1f)
                if (sub.inverted) wv = 1f - wv
                contribution[i] = wv.coerceIn(0f, 1f)
            }

            if (!started) {
                accumulated = contribution
                started = true
            } else {
                accumulated = combineMasks(accumulated, contribution, w, h, sub.combineMode)
            }
        }

        return accumulated
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun maskTypeToNative(type: MaskType): Int = when (type) {
        MaskType.BRUSH -> NATIVE_MASK_BRUSH
        MaskType.LINEAR -> NATIVE_MASK_LINEAR
        MaskType.RADIAL -> NATIVE_MASK_RADIAL
        MaskType.LUMINANCE_RANGE -> NATIVE_MASK_LUMINOSITY
        MaskType.COLOR_RANGE -> NATIVE_MASK_COLOR_RANGE
        MaskType.WHOLE_IMAGE -> NATIVE_MASK_WHOLE_IMAGE
        // AI types fall back to whole image in native; they go through
        // inferenceService.infer() before reaching here.
        MaskType.SUBJECT, MaskType.SKY, MaskType.FOREGROUND -> NATIVE_MASK_WHOLE_IMAGE
    }

    /**
     * Pack [MaskParams] into the float array expected by the JNI bridge.
     * Order must match the C++ nativeGenerateMask documentation.
     */
    fun paramsToNativeArray(params: MaskParams): FloatArray = floatArrayOf(
        // [0] opacity
        1.0f, // opacity is handled at container/sub-mask level
        // [1] inverted (0.0 / 1.0)
        0.0f, // inversion is handled at container/sub-mask level
        // [2] feather
        params.feather,
        // [3-5] brush params
        params.brushSize,
        params.brushHardness,
        params.brushOpacity,
        // [6-9] linear gradient params
        params.linearStart?.x ?: 0.2f,
        params.linearStart?.y ?: 0.2f,
        params.linearEnd?.x ?: 0.8f,
        params.linearEnd?.y ?: 0.8f,
        // [10-13] radial gradient params
        params.radialCenter?.x ?: 0.5f,
        params.radialCenter?.y ?: 0.5f,
        params.radialRadius,
        params.radialRadius, // radiusY same as radius (elliptical not in model yet)
        // [14-15] luminosity params
        params.luminanceMin,
        params.luminanceMax,
        // [16-19] color range params
        ((params.colorTarget ?: 0x808080) shr 16 and 0xFF) / 255f,
        ((params.colorTarget ?: 0x808080) shr 8 and 0xFF) / 255f,
        ((params.colorTarget ?: 0x808080) and 0xFF) / 255f,
        params.colorRange
    )

    /**
     * Kotlin fallback mask generation when native is unavailable.
     */
    private fun generateKotlinFallback(
        width: Int, height: Int, type: MaskType, params: MaskParams
    ): FloatArray {
        val n = width * height
        val mask = FloatArray(n)
        when (type) {
            MaskType.WHOLE_IMAGE -> mask.fill(1f)
            MaskType.LINEAR -> {
                val start = params.linearStart ?: PointF(0.2f, 0.2f)
                val end = params.linearEnd ?: PointF(0.8f, 0.8f)
                val sx = start.x * width; val sy = start.y * height
                val ex = end.x * width; val ey = end.y * height
                val dx = ex - sx; val dy = ey - sy
                val len2 = dx * dx + dy * dy
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val t = if (len2 > 1e-6f) ((x - sx) * dx + (y - sy) * dy) / len2 else 0.5f
                        mask[y * width + x] = (1f - t.coerceIn(0f, 1f))
                    }
                }
            }
            MaskType.RADIAL -> {
                val c = params.radialCenter ?: PointF(0.5f, 0.5f)
                val cx = c.x * width; val cy = c.y * height
                val radius = params.radialRadius * min(width, height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                        mask[y * width + x] = ((radius - d) / radius).coerceIn(0f, 1f)
                    }
                }
            }
            else -> mask.fill(1f) // Default: full mask for unsupported types in fallback
        }
        return mask
    }

    /** Simple 3-pass box blur for Kotlin fallback. */
    private fun kotlinBoxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        var current = src.copyOf()
        val tmp = FloatArray(src.size)
        repeat(3) {
            // Horizontal
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sum = 0f; var n = 0
                    for (dx in -radius..radius) {
                        val xx = x + dx
                        if (xx in 0 until w) { sum += current[y * w + xx]; n++ }
                    }
                    tmp[y * w + x] = if (n > 0) sum / n else 0f
                }
            }
            // Vertical
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sum = 0f; var n = 0
                    for (dy in -radius..radius) {
                        val yy = y + dy
                        if (yy in 0 until h) { sum += tmp[yy * w + x]; n++ }
                    }
                    current[y * w + x] = if (n > 0) sum / n else 0f
                }
            }
        }
        return current
    }
}
