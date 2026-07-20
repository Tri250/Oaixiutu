package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.data.model.MaskAdjustments
import com.alcedo.studio.data.model.MaskCombineMode
import com.alcedo.studio.data.model.MaskContainer
import com.alcedo.studio.data.model.MaskType
import com.alcedo.studio.data.model.SubMask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Combines a list of [MaskContainer]s into a single soft mask and applies each
 * container's [MaskAdjustments] to the original bitmap within its masked region.
 *
 * Sub-mask combination rules (within a container):
 *   - ADDITIVE    → union with the accumulated mask (a + b capped at 1)
 *   - SUBTRACTIVE → subtract from the accumulated mask (a - b)
 *   - INTERSECT   → keep only the overlap (min(a, b))
 *
 * Each sub-mask is first weighted by its opacity and optionally inverted; the
 * container-level opacity / inversion is then applied to the per-container
 * result.
 */
class MaskRenderService(
    private val inferenceService: MaskInferenceService = MaskInferenceService()
) {

    companion object {
        private const val TAG = "MaskRenderService"
    }

    // LRU-ish cache of computed sub-mask results keyed by sub-mask id, so that
    // interactive opacity / inversion edits don't re-run AI segmentation.
    private val subMaskCache = object : LinkedHashMap<String, MaskInferenceService.MaskResult>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MaskInferenceService.MaskResult>?): Boolean {
            return size > 64
        }
    }

    /** Drop any cached sub-mask (e.g. when its type or params change). */
    fun invalidate(subMaskId: String? = null) {
        synchronized(subMaskCache) {
            if (subMaskId == null) subMaskCache.clear() else subMaskCache.remove(subMaskId)
        }
    }

    /**
     * Render the combined mask of all visible [containers] as a single ARGB
     * bitmap. Masked pixels are opaque white (alpha = combined weight); the
     * RGB channels are white so the caller can tint the overlay as needed.
     */
    suspend fun renderCombinedMask(
        containers: List<MaskContainer>,
        bitmap: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        val combined = FloatArray(w * h)
        for (container in containers) {
            if (!container.visible) continue
            val containerMask = renderContainerMask(container, bitmap, w, h) ?: continue
            val containerOpacity = container.opacity.coerceIn(0f, 1f)
            for (i in combined.indices) {
                var wv = containerMask.weights[i] * containerOpacity
                if (container.inverted) wv = 1f - wv
                // Visible containers are combined additively (union).
                combined[i] = min(1f, combined[i] + wv)
            }
        }
        weightsToBitmap(combined, w, h)
    }

    /**
     * Render the combined mask as a red-tinted overlay bitmap suitable for
     * drawing on top of the preview image (used by the mask panel).
     */
    suspend fun renderMaskOverlay(
        containers: List<MaskContainer>,
        bitmap: Bitmap,
        argbColor: Int = 0xCCFF3030.toInt()
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        val combined = FloatArray(w * h)
        for (container in containers) {
            if (!container.visible) continue
            val containerMask = renderContainerMask(container, bitmap, w, h) ?: continue
            val containerOpacity = container.opacity.coerceIn(0f, 1f)
            for (i in combined.indices) {
                var wv = containerMask.weights[i] * containerOpacity
                if (container.inverted) wv = 1f - wv
                combined[i] = min(1f, combined[i] + wv)
            }
        }
        val r = (argbColor shr 16 and 0xFF)
        val g = (argbColor shr 8 and 0xFF)
        val b = (argbColor and 0xFF)
        val baseA = (argbColor ushr 24 and 0xFF) / 255f
        val pixels = IntArray(w * h)
        for (i in combined.indices) {
            val a = (combined[i] * baseA * 255f + 0.5f).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    /**
     * Apply every container's [MaskAdjustments] to [original] within its masked
     * region and return the composited result. Containers are applied in order;
     * each one adjusts a local copy and is blended back by its mask weight.
     */
    suspend fun applyMasks(
        containers: List<MaskContainer>,
        original: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = original.width
        val h = original.height
        val basePixels = IntArray(w * h)
        original.getPixels(basePixels, 0, w, 0, 0, w, h)
        val result = basePixels.copyOf()

        for (container in containers) {
            if (!container.visible) continue
            val mask = renderContainerMask(container, original, w, h) ?: continue
            val opacity = container.opacity.coerceIn(0f, 1f)
            // Apply adjustments to the source pixels, then blend by mask.
            val adjusted = applyAdjustments(basePixels, w, h, container.adjustments)
            for (i in result.indices) {
                var weight = mask.weights[i] * opacity
                if (container.inverted) weight = 1f - weight
                if (weight <= 0f) continue
                if (weight >= 1f) {
                    result[i] = adjusted[i]
                    continue
                }
                result[i] = blend(basePixels[i], adjusted[i], weight)
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(result, 0, w, 0, 0, w, h)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Per-container mask combination
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun renderContainerMask(
        container: MaskContainer,
        bitmap: Bitmap,
        w: Int,
        h: Int
    ): MaskInferenceService.MaskResult? {
        if (container.subMasks.isEmpty()) return null
        val accumulated = FloatArray(w * h)
        var started = false

        for (sub in container.subMasks) {
            if (!sub.visible) continue
            val subMask = computeSubMask(sub, bitmap, w, h) ?: continue
            val contribution = FloatArray(w * h)
            for (i in contribution.indices) {
                var wv = subMask.weights[i] * sub.opacity.coerceIn(0f, 1f)
                if (sub.inverted) wv = 1f - wv
                contribution[i] = wv.coerceIn(0f, 1f)
            }

            if (!started) {
                // The first sub-mask seeds the accumulated mask.
                for (i in accumulated.indices) accumulated[i] = contribution[i]
                started = true
            } else {
                for (i in accumulated.indices) {
                    accumulated[i] = combine(accumulated[i], contribution[i], sub.combineMode)
                }
            }
        }

        if (!started) return null
        return MaskInferenceService.MaskResult(w, h, accumulated)
    }

    private fun combine(a: Float, b: Float, mode: MaskCombineMode): Float = when (mode) {
        MaskCombineMode.ADDITIVE -> min(1f, a + b)
        MaskCombineMode.SUBTRACTIVE -> (a - b).coerceIn(0f, 1f)
        MaskCombineMode.INTERSECT -> min(a, b)
    }

    private suspend fun computeSubMask(
        sub: SubMask,
        bitmap: Bitmap,
        w: Int,
        h: Int
    ): MaskInferenceService.MaskResult? {
        val cacheKey = "${sub.id}|${sub.type}|${sub.params.hashCode()}"
        synchronized(subMaskCache) {
            subMaskCache[cacheKey]?.let { return it }
        }
        val result = try {
            when (sub.type) {
                MaskType.SUBJECT, MaskType.SKY, MaskType.FOREGROUND ->
                    inferenceService.infer(bitmap, sub.type)
                MaskType.LINEAR, MaskType.RADIAL, MaskType.BRUSH,
                MaskType.COLOR_RANGE, MaskType.LUMINANCE_RANGE, MaskType.WHOLE_IMAGE ->
                    inferenceService.rasterize(bitmap, sub.type, sub.params)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "computeSubMask failed for ${sub.type}", e)
            // Fall back to a full mask so the container still renders.
            MaskInferenceService.MaskResult(w, h, FloatArray(w * h) { 1f })
        } ?: MaskInferenceService.MaskResult(w, h, FloatArray(w * h) { 1f })
        // Normalize the result to the current w/h (AI masks already match).
        val normalized = if (result.width == w && result.height == h) {
            result
        } else {
            MaskInferenceService.MaskResult(w, h, inferenceServiceRescale(result, w, h))
        }
        synchronized(subMaskCache) {
            subMaskCache[cacheKey] = normalized
        }
        return normalized
    }

    /** Exposed so computeSubMask can rescale without promoting helpers. */
    private fun inferenceServiceRescale(
        result: MaskInferenceService.MaskResult, w: Int, h: Int
    ): FloatArray {
        // Simple nearest-neighbor resample for the rare size mismatch.
        val out = FloatArray(w * h)
        val sx = result.width.toFloat() / w
        val sy = result.height.toFloat() / h
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[y * w + x] = result.weights[(y * sy).toInt().coerceIn(0, result.height - 1) *
                    result.width + (x * sx).toInt().coerceIn(0, result.width - 1)]
            }
        }
        return out
    }

    // ──────────────────────────────────────────────────────────────────────
    // Mask → bitmap
    // ──────────────────────────────────────────────────────────────────────

    private fun weightsToBitmap(weights: FloatArray, w: Int, h: Int): Bitmap {
        val pixels = IntArray(w * h)
        for (i in weights.indices) {
            val a = (weights[i] * 255f + 0.5f).toInt().coerceIn(0, 255)
            // White where masked, alpha carries the weight.
            pixels[i] = (a shl 24) or 0x00FFFFFF
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Adjustment application (real per-pixel tonal/color ops)
    // ──────────────────────────────────────────────────────────────────────

    private fun applyAdjustments(
        pixels: IntArray, w: Int, h: Int, adj: MaskAdjustments
    ): IntArray {
        // Fast path: nothing to do.
        if (adj.exposure == 0f && adj.contrast == 0f && adj.highlights == 0f &&
            adj.shadows == 0f && adj.saturation == 0f && adj.temperature == 0f &&
            adj.tint == 0f && adj.clarity == 0f && adj.sharpen == 0f
        ) {
            return pixels.copyOf()
        }

        val exposureGain = 2.0.pow(adj.exposure.toDouble()).toFloat()
        val contrastFactor = 1f + adj.contrast.coerceIn(-1f, 1f)
        val satFactor = 1f + adj.saturation.coerceIn(-1f, 1f)
        val tempShift = adj.temperature  // +warmer → more R, less B
        val tintShift = adj.tint          // + → more G
        val highlightAmt = adj.highlights.coerceIn(-1f, 1f)
        val shadowAmt = adj.shadows.coerceIn(-1f, 1f)
        val clarityAmt = adj.clarity.coerceIn(-1f, 1f)
        val sharpenAmt = adj.sharpen.coerceIn(0f, 1f)

        val out = IntArray(pixels.size)

        // Clarity / sharpening need a luminance baseline; compute a small
        // blurred luminance buffer for a lightweight local-contrast pass.
        val lum = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        val blurredLum = if (clarityAmt != 0f || sharpenAmt != 0f) boxBlur3x3(lum, w, h) else null

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            var r = ((p shr 16) and 0xFF) / 255f
            var g = ((p shr 8) and 0xFF) / 255f
            var b = (p and 0xFF) / 255f

            // White balance (temperature/tint) in linear-ish RGB.
            r = (r + tempShift * 0.05f).coerceIn(0f, 1f)
            b = (b - tempShift * 0.05f).coerceIn(0f, 1f)
            g = (g + tintShift * 0.05f).coerceIn(0f, 1f)

            // Exposure (multiplicative gain).
            r *= exposureGain; g *= exposureGain; b *= exposureGain

            // Contrast around 0.5 midpoint.
            r = ((r - 0.5f) * contrastFactor + 0.5f)
            g = ((g - 0.5f) * contrastFactor + 0.5f)
            b = ((b - 0.5f) * contrastFactor + 0.5f)

            // Highlights / shadows tonal weighting.
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            val highlightWeight = ((l - 0.5f) * 2f).coerceIn(0f, 1f)
            val shadowWeight = ((0.5f - l) * 2f).coerceIn(0f, 1f)
            val highlightDelta = highlightAmt * highlightWeight * 0.5f
            val shadowDelta = shadowAmt * shadowWeight * 0.5f
            r += highlightDelta + shadowDelta
            g += highlightDelta + shadowDelta
            b += highlightDelta + shadowDelta

            // Clarity (local contrast against blurred luminance).
            if (blurredLum != null && (clarityAmt != 0f || sharpenAmt != 0f)) {
                val localDelta = lum[i] - blurredLum[i]
                val localBoost = clarityAmt * 1.5f + sharpenAmt * 2.5f
                val delta = localDelta * localBoost
                r += delta; g += delta; b += delta
            }

            // Saturation around the pixel luminance.
            val l2 = 0.299f * r + 0.587f * g + 0.114f * b
            r = l2 + (r - l2) * satFactor
            g = l2 + (g - l2) * satFactor
            b = l2 + (b - l2) * satFactor

            r = r.coerceIn(0f, 1f)
            g = g.coerceIn(0f, 1f)
            b = b.coerceIn(0f, 1f)

            out[i] = (a shl 24) or
                ((r * 255f + 0.5f).toInt().coerceIn(0, 255) shl 16) or
                ((g * 255f + 0.5f).toInt().coerceIn(0, 255) shl 8) or
                (b * 255f + 0.5f).toInt().coerceIn(0, 255)
        }
        return out
    }

    /** 3x3 box blur on a luminance buffer (used for clarity/sharpen baseline). */
    private fun boxBlur3x3(src: FloatArray, w: Int, h: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        // Horizontal
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f; var n = 0
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx in 0 until w) { s += src[y * w + xx]; n++ }
                }
                tmp[y * w + x] = s / n
            }
        }
        // Vertical
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f; var n = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy in 0 until h) { s += tmp[yy * w + x]; n++ }
                }
                out[y * w + x] = s / n
            }
        }
        return out
    }

    /** Alpha-blend [src] and [dst] by [t] (0..1, 0 = src, 1 = dst). */
    private fun blend(src: Int, dst: Int, t: Float): Int {
        val a = (src ushr 24) and 0xFF
        val r1 = (src shr 16) and 0xFF
        val g1 = (src shr 8) and 0xFF
        val b1 = src and 0xFF
        val r2 = (dst shr 16) and 0xFF
        val g2 = (dst shr 8) and 0xFF
        val b2 = dst and 0xFF
        val r = (r1 + (r2 - r1) * t).toInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * t).toInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * t).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
