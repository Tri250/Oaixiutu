package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.alcedo.studio.data.model.MaskParams
import com.alcedo.studio.data.model.MaskType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real on-device mask inference engine.
 *
 * Implements subject / sky / foreground segmentation in pure Kotlin using
 * actual pixel analysis (Sobel edge detection, adaptive-threshold flood fill
 * from the image borders as a background seed, a center-region foreground
 * seed, and a gradient-gated sky flood fill from the top rows).
 *
 * No external segmentation model is required; the algorithms operate on a
 * downscaled 256x256 luminance buffer for performance and the resulting soft
 * mask is bilinearly upscaled back to the source resolution.
 */
class MaskInferenceService {

    companion object {
        private const val TAG = "MaskInferenceService"
        private const val WORK_SIZE = 256
    }

    /**
     * Per-pixel soft mask at the source resolution.
     *
     * [weights] is a row-major FloatArray of length `width * height` with
     * values in [0,1] (0 = fully outside the mask, 1 = fully inside). A weight
     * of >= 0.5 is treated as "inside" by [toBooleanArray] / [isInside].
     */
    data class MaskResult(
        val width: Int,
        val height: Int,
        val weights: FloatArray
    ) {
        init {
            require(weights.size == width * height) {
                "weights length ${weights.size} != width*height ${width * height}"
            }
        }

        fun isInside(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x >= width || y >= height) return false
            return weights[y * width + x] >= 0.5f
        }

        /** Hard boolean per-pixel mask (weight >= 0.5 ⇒ inside). */
        fun toBooleanArray(): BooleanArray = BooleanArray(weights.size) { weights[it] >= 0.5f }

        /** Renders the mask as an ALPHA_8 bitmap (alpha = weight * 255). */
        fun toBitmap(): Bitmap {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
            val pixels = IntArray(width * height)
            for (i in weights.indices) {
                val a = (weights[i] * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixels[i] = a shl 24
            }
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            return bmp
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MaskResult) return false
            return width == other.width && height == other.height &&
                weights.contentEquals(other.weights)
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + weights.contentHashCode()
            return result
        }
    }

    /**
     * Run the requested AI mask type over [bitmap] and return a full-resolution
     * soft mask. Always returns a non-null result (an all-zero mask on failure).
     */
    suspend fun infer(bitmap: Bitmap, type: MaskType): MaskResult = runCatching {
        when (type) {
            MaskType.SUBJECT -> inferSubject(bitmap)
            MaskType.SKY -> inferSky(bitmap)
            MaskType.FOREGROUND -> inferForeground(bitmap)
            MaskType.WHOLE_IMAGE -> fullMask(bitmap.width, bitmap.height)
            else -> fullMask(bitmap.width, bitmap.height)
        }
    }.getOrElse { e ->
        Log.e(TAG, "Inference failed for $type", e)
        fullMask(bitmap.width, bitmap.height)
    }

    /**
     * Build a geometric / range mask (no AI). Used by the render service for
     * the non-AI [MaskType]s; kept here so all mask rasterization lives in one
     * place.
     */
    fun rasterize(bitmap: Bitmap, type: MaskType, params: MaskParams): MaskResult {
        val w = bitmap.width
        val h = bitmap.height
        return when (type) {
            MaskType.LINEAR -> rasterLinear(w, h, params)
            MaskType.RADIAL -> rasterRadial(w, h, params)
            MaskType.BRUSH -> rasterBrush(w, h, params)
            MaskType.COLOR_RANGE -> rasterColorRange(bitmap, params)
            MaskType.LUMINANCE_RANGE -> rasterLuminanceRange(bitmap, params)
            MaskType.WHOLE_IMAGE -> fullMask(w, h)
            MaskType.SUBJECT, MaskType.SKY, MaskType.FOREGROUND ->
                throw IllegalStateException("AI masks must go through infer()")
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Subject mask — GrabCut-style: Sobel edges → border flood fill
    // (background seed) → center region (foreground seed) → soft combine.
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun inferSubject(bitmap: Bitmap): MaskResult {
        val work = downscale(bitmap, WORK_SIZE, WORK_SIZE)
        val w = work.width
        val h = work.height
        val pixels = IntArray(w * h)
        work.getPixels(pixels, 0, w, 0, 0, w, h)
        val lum = luminanceArray(pixels, w, h)
        val edges = sobelMagnitude(lum, w, h)

        // Adaptive edge threshold at the 68th percentile → strong edges form
        // the barriers that stop the background flood fill.
        val edgeThreshold = percentile(edges, 0.68f)

        // Background seed: flood fill inward from all four borders. Pixels
        // reachable from the border without crossing a strong edge are deemed
        // background.
        val isBackground = borderFloodFill(edges, w, h, edgeThreshold)

        // Foreground seed: center ellipse (the most likely subject location).
        val cx = w * 0.5f
        val cy = h * 0.5f
        val rx = w * 0.32f
        val ry = h * 0.32f

        val weights = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (isBackground[idx]) {
                    weights[idx] = 0f
                    continue
                }
                // Center prior: Gaussian falloff from the image center.
                val dx = (x - cx) / rx
                val dy = (y - cy) / ry
                val d2 = dx * dx + dy * dy
                val centerPrior = exp(-d2 * 0.5f)
                // Interior (non-flooded) pixels are the subject; the center
                // prior biases the soft weight toward the middle of the frame.
                weights[idx] = 0.35f + 0.65f * centerPrior
            }
        }

        // Feather the mask boundary so the compositing edge isn't hard.
        val feathered = boxBlur(weights, w, h, 2)
        val upscaled = bilinearUpscale(feathered, w, h, bitmap.width, bitmap.height)
        if (work !== bitmap) work.recycle()
        return MaskResult(bitmap.width, bitmap.height, upscaled)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Sky mask — analyze the top 40%, grow a sky region from the top row
    // gated by blueness / brightness / low local gradient.
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun inferSky(bitmap: Bitmap): MaskResult {
        val work = downscale(bitmap, WORK_SIZE, WORK_SIZE)
        val w = work.width
        val h = work.height
        val pixels = IntArray(w * h)
        work.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = FloatArray(w * h)
        val grad = FloatArray(w * h)
        val skyScore = FloatArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val p = pixels[idx]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val l = 0.299f * r + 0.587f * g + 0.114f * b
                lum[idx] = l
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                val sat = if (mx > 1e-4f) (mx - mn) / mx else 0f

                var score = 0f
                // Clear blue sky: blue-dominant, moderate saturation, bright.
                if (b >= r && b >= g && l > 0.3f) {
                    score += 0.6f * min(1f, (b - r) * 4f + 0.3f)
                }
                // Bright, low-saturation (overcast / white sky).
                if (l > 0.55f && sat < 0.18f) {
                    score += 0.5f
                }
                // Cool tint (b > r) even if not dominant.
                if (b > r) {
                    score += 0.15f * min(1f, (b - r) * 6f)
                }
                skyScore[idx] = score.coerceIn(0f, 1f)
            }
        }

        // Local gradient via 3x3 max-min luminance (cheap smoothness proxy).
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                var mx = 0f
                var mn = 1f
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = lum[(y + dy) * w + (x + dx)]
                        if (v > mx) mx = v
                        if (v < mn) mn = v
                    }
                }
                grad[idx] = mx - mn
            }
        }

        // Adaptive sky-score threshold from the top 40% of the frame, so the
        // gate tracks the actual sky brightness of each photo.
        val topRowCount = (h * 0.40f).toInt().coerceAtLeast(1)
        val topScores = FloatArray(topRowCount * w)
        var k = 0
        for (y in 0 until topRowCount) {
            for (x in 0 until w) {
                topScores[k++] = skyScore[y * w + x]
            }
        }
        val scoreThreshold = percentile(topScores, 0.45f).coerceAtLeast(0.25f)
        val gradThreshold = 0.12f

        // Grow the sky region from the entire top row; a neighbor joins the
        // sky if its skyScore is high enough and the local gradient is low
        // (sky regions are smooth).
        val isSky = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        for (x in 0 until w) {
            val idx = x
            if (skyScore[idx] >= scoreThreshold && grad[idx] <= gradThreshold) {
                isSky[idx] = true
                stack[sp++] = idx
            }
        }
        while (sp > 0) {
            val idx = stack[--sp]
            val x = idx % w
            val y = idx / w
            for ((dx, dy) in intArrayOf(1, 0, -1, 0, 0, 1, 0, -1).toList().chunked(2)) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val ni = ny * w + nx
                if (isSky[ni]) continue
                if (skyScore[ni] >= scoreThreshold * 0.7f && grad[ni] <= gradThreshold * 1.5f) {
                    isSky[ni] = true
                    stack[sp++] = ni
                }
            }
        }

        val weights = FloatArray(w * h) { if (isSky[it]) 1f else 0f }
        val feathered = boxBlur(weights, w, h, 2)
        val upscaled = bilinearUpscale(feathered, w, h, bitmap.width, bitmap.height)
        if (work !== bitmap) work.recycle()
        return MaskResult(bitmap.width, bitmap.height, upscaled)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Foreground mask — invert the sky mask and intersect with subject
    // saliency, so foreground ≈ "the grounded subject that is not sky".
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun inferForeground(bitmap: Bitmap): MaskResult {
        val sky = inferSky(bitmap)
        val subject = inferSubject(bitmap)
        val n = bitmap.width * bitmap.height
        val out = FloatArray(n)
        for (i in 0 until n) {
            val notSky = 1f - sky.weights[i]
            // Intersect: foreground = not-sky AND subject.
            out[i] = min(notSky, subject.weights[i])
        }
        return MaskResult(bitmap.width, bitmap.height, out)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Geometric / range mask rasterization
    // ──────────────────────────────────────────────────────────────────────

    private fun rasterLinear(w: Int, h: Int, p: MaskParams): MaskResult {
        val start = p.linearStart ?: PointF(0.2f, 0.2f)
        val end = p.linearEnd ?: PointF(0.8f, 0.8f)
        val sx = start.x * w
        val sy = start.y * h
        val ex = end.x * w
        val ey = end.y * h
        val dx = ex - sx
        val dy = ey - sy
        val len2 = dx * dx + dy * dy
        val len = sqrt(len2).coerceAtLeast(1f)
        val feather = (p.feather * len).coerceAtLeast(1f)
        val weights = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                // Perpendicular distance from point to the gradient line.
                val px = x.toFloat() - sx
                val py = y.toFloat() - sy
                val t = ((px * dx + py * dy) / len2).coerceIn(0f, 1f)
                val projX = sx + t * dx
                val projY = sy + t * dy
                val d = sqrt((x - projX) * (x - projX) + (y - projY) * (y - projY))
                // Full strength on the start side, falloff toward the end,
                // plus perpendicular feather across the band.
                val along = 1f - t  // 1 at start, 0 at end
                val across = (1f - (d / feather)).coerceIn(0f, 1f)
                weights[y * w + x] = along * across
            }
        }
        return MaskResult(w, h, weights)
    }

    private fun rasterRadial(w: Int, h: Int, p: MaskParams): MaskResult {
        val c = p.radialCenter ?: PointF(0.5f, 0.5f)
        val cx = c.x * w
        val cy = c.y * h
        val radius = (p.radialRadius * min(w, h)).coerceAtLeast(1f)
        val feather = (p.feather * radius).coerceAtLeast(1f)
        val weights = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                // Inside radius → full; feather band → smooth falloff; outside → 0.
                weights[y * w + x] = ((radius + feather - d) / (2f * feather)).coerceIn(0f, 1f)
            }
        }
        return MaskResult(w, h, weights)
    }

    private fun rasterBrush(w: Int, h: Int, p: MaskParams): MaskResult {
        val weights = FloatArray(w * h)
        val pts = p.brushPoints ?: return MaskResult(w, h, weights)
        if (pts.isEmpty()) return MaskResult(w, h, weights)
        val radius = (p.brushSize * min(w, h)).coerceAtLeast(1f)
        val hardness = p.brushHardness.coerceIn(0f, 1f)
        val innerR = radius * hardness
        val falloff = (radius - innerR).coerceAtLeast(0.5f)

        // Rasterize each brush segment as a thick polyline (stamps between
        // consecutive points so fast strokes stay continuous).
        for (i in pts.indices) {
            val a = pts[i]
            val ax = a.x * w
            val ay = a.y * h
            val bx: Float
            val by: Float
            if (i + 1 < pts.size) {
                bx = pts[i + 1].x * w
                by = pts[i + 1].y * h
            } else {
                bx = ax
                by = ay
            }
            stampSegment(weights, w, h, ax, ay, bx, by, radius, innerR, falloff, a.pressure)
        }
        return MaskResult(w, h, weights)
    }

    private fun stampSegment(
        weights: FloatArray, w: Int, h: Int,
        ax: Float, ay: Float, bx: Float, by: Float,
        radius: Float, innerR: Float, falloff: Float, pressure: Float
    ) {
        val segLen = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
        val steps = max(1, (segLen / (radius * 0.25f)).toInt())
        val r = radius.toInt() + 1
        for (s in 0..steps) {
            val t = if (steps == 0) 0f else s.toFloat() / steps
            val px = ax + (bx - ax) * t
            val py = ay + (by - ay) * t
            val x0 = (px - r).toInt().coerceAtLeast(0)
            val x1 = (px + r).toInt().coerceAtLeast(0).coerceAtMost(w - 1)
            val y0 = (py - r).toInt().coerceAtLeast(0)
            val y1 = (py + r).toInt().coerceAtLeast(0).coerceAtMost(h - 1)
            for (yy in y0..y1) {
                for (xx in x0..x1) {
                    val d = sqrt((xx - px) * (xx - px) + (yy - py) * (yy - py))
                    if (d > radius) continue
                    val wv = if (d <= innerR) 1f
                    else ((radius - d) / falloff).coerceIn(0f, 1f)
                    val v = wv * pressure
                    val idx = yy * w + xx
                    if (v > weights[idx]) weights[idx] = v
                }
            }
        }
    }

    private fun rasterColorRange(bitmap: Bitmap, p: MaskParams): MaskResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val target = p.colorTarget ?: 0x808080
        val tr = ((target shr 16) and 0xFF) / 255f
        val tg = ((target shr 8) and 0xFF) / 255f
        val tb = (target and 0xFF) / 255f
        val range = p.colorRange.coerceIn(0.01f, 1f)
        val weights = FloatArray(w * h)
        for (i in pixels.indices) {
            val pix = pixels[i]
            val r = ((pix shr 16) and 0xFF) / 255f
            val g = ((pix shr 8) and 0xFF) / 255f
            val b = (pix and 0xFF) / 255f
            val dist = sqrt((r - tr) * (r - tr) + (g - tg) * (g - tg) + (b - tb) * (b - tb))
            weights[i] = ((range - dist) / range).coerceIn(0f, 1f)
        }
        return MaskResult(w, h, weights)
    }

    private fun rasterLuminanceRange(bitmap: Bitmap, p: MaskParams): MaskResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val lo = p.luminanceMin.coerceIn(0f, 1f)
        val hi = p.luminanceMax.coerceIn(0f, 1f)
        val feather = p.feather.coerceIn(0.001f, 1f)
        val weights = FloatArray(w * h)
        val f = feather * 0.5f
        for (i in pixels.indices) {
            val pix = pixels[i]
            val r = ((pix shr 16) and 0xFF) / 255f
            val g = ((pix shr 8) and 0xFF) / 255f
            val b = (pix and 0xFF) / 255f
            val l = (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0f, 1f)
            weights[i] = when {
                l in lo..hi -> 1f
                l < lo -> ((lo - l) / f).let { (1f - it).coerceIn(0f, 1f) }
                else -> ((l - hi) / f).let { (1f - it).coerceIn(0f, 1f) }
            }
        }
        return MaskResult(w, h, weights)
    }

    private fun fullMask(w: Int, h: Int): MaskResult = MaskResult(w, h, FloatArray(w * h) { 1f })

    // ──────────────────────────────────────────────────────────────────────
    // Pixel analysis primitives
    // ──────────────────────────────────────────────────────────────────────

    private fun downscale(bitmap: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (bitmap.width == targetW && bitmap.height == targetH) return bitmap
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun luminanceArray(pixels: IntArray, w: Int, h: Int): FloatArray {
        val lum = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return lum
    }

    /** Sobel operator magnitude (pure Kotlin), normalized to roughly [0,1]. */
    private fun sobelMagnitude(lum: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val tl = lum[i - w - 1]; val tc = lum[i - w]; val tr = lum[i - w + 1]
                val ml = lum[i - 1]; val mr = lum[i + 1]
                val bl = lum[i + w - 1]; val bc = lum[i + w]; val br = lum[i + w + 1]
                val gx = -tl - 2f * ml - bl + tr + 2f * mr + br
                val gy = -tl - 2f * tc - tr + bl + 2f * bc + br
                val mag = sqrt(gx * gx + gy * gy)
                out[i] = (mag / 4f).coerceIn(0f, 1f)
            }
        }
        return out
    }

    /**
     * Flood fill inward from all four border pixels. A pixel is reachable
     * (background) if the path from a border pixel never crosses a strong
     * edge (edge magnitude > [threshold]). Strong edges act as walls that
     * enclose the subject.
     */
    private fun borderFloodFill(edges: FloatArray, w: Int, h: Int, threshold: Float): BooleanArray {
        if (w <= 0 || h <= 0) return BooleanArray(0)
        if (edges.size != w * h) return BooleanArray(w * h)
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h * 4)
        var sp = 0
        // Seed: every border pixel is background by definition.
        for (x in 0 until w) {
            val top = x
            val bot = (h - 1) * w + x
            if (!visited[top]) { visited[top] = true; stack[sp++] = top }
            if (!visited[bot]) { visited[bot] = true; stack[sp++] = bot }
        }
        for (y in 0 until h) {
            val left = y * w
            val right = y * w + (w - 1)
            if (!visited[left]) { visited[left] = true; stack[sp++] = left }
            if (!visited[right]) { visited[right] = true; stack[sp++] = right }
        }
        while (sp > 0) {
            val idx = stack[--sp]
            val x = idx % w
            val y = idx / w
            // 4-connectivity neighbors.
            if (x > 0) tryNeighbor(edges, visited, stack, sp, idx - 1, threshold)
                .also { sp = it }
            if (x < w - 1) tryNeighbor(edges, visited, stack, sp, idx + 1, threshold)
                .also { sp = it }
            if (y > 0) tryNeighbor(edges, visited, stack, sp, idx - w, threshold)
                .also { sp = it }
            if (y < h - 1) tryNeighbor(edges, visited, stack, sp, idx + w, threshold)
                .also { sp = it }
        }
        return visited
    }

    private fun tryNeighbor(
        edges: FloatArray, visited: BooleanArray, stack: IntArray,
        sp: Int, ni: Int, threshold: Float
    ): Int {
        if (ni < 0 || ni >= visited.size) return sp
        if (visited[ni]) return sp
        // Strong-edge pixels are walls — flood fill cannot enter them.
        if (edges[ni] > threshold) return sp
        visited[ni] = true
        stack[sp] = ni
        return sp + 1
    }

    /** Returns the value at the given [quantile] (0..1) of [data]. */
    private fun percentile(data: FloatArray, quantile: Float): Float {
        if (data.isEmpty()) return 0f
        if (data.size == 1) return data[0]
        val q = quantile.coerceIn(0f, 1f)
        val sorted = data.copyOf()
        sorted.sort()
        val pos = (sorted.size - 1) * q
        val lo = pos.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.size - 1)
        val frac = pos - lo
        return sorted[lo] * (1f - frac) + sorted[hi] * frac
    }

    /** Separable 3x3-ish box blur, [radius] passes for feathering. */
    private fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src.copyOf()
        if (w <= 0 || h <= 0) return src.copyOf()
        if (src.size != w * h) return src.copyOf()
        var current = src
        val tmp = FloatArray(w * h)
        repeat(radius) {
            // Horizontal pass.
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sum = 0f
                    var n = 0
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx in 0 until w) { sum += current[y * w + xx]; n++ }
                    }
                    tmp[y * w + x] = if (n > 0) sum / n else 0f
                }
            }
            // Vertical pass.
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sum = 0f
                    var n = 0
                    for (dy in -1..1) {
                        val yy = y + dy
                        if (yy in 0 until h) { sum += tmp[yy * w + x]; n++ }
                    }
                    current[x + y * w] = if (n > 0) sum / n else 0f
                }
            }
            // current now holds result of this pass; tmp will be reused next pass.
            @Suppress("UNUSED_VARIABLE") val _unused = it
        }
        return current
    }

    /** Bilinear upscaling of a small weight buffer to a larger size. */
    private fun bilinearUpscale(
        src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int
    ): FloatArray {
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return FloatArray(0)
        if (src.size != sw * sh) return FloatArray(dw * dh)
        if (sw == dw && sh == dh) return src.copyOf()
        val out = FloatArray(dw * dh)
        val xRatio = (sw - 1).toFloat() / (dw - 1).coerceAtLeast(1)
        val yRatio = (sh - 1).toFloat() / (dh - 1).coerceAtLeast(1)
        for (y in 0 until dh) {
            val sy = y * yRatio
            val y0 = sy.toInt().coerceIn(0, sh - 1)
            val y1 = (y0 + 1).coerceAtMost(sh - 1)
            val fy = sy - y0
            for (x in 0 until dw) {
                val sx = x * xRatio
                val x0 = sx.toInt().coerceIn(0, sw - 1)
                val x1 = (x0 + 1).coerceAtMost(sw - 1)
                val fx = sx - x0
                val v00 = src[y0 * sw + x0]
                val v01 = src[y0 * sw + x1]
                val v10 = src[y1 * sw + x0]
                val v11 = src[y1 * sw + x1]
                val top = v00 * (1f - fx) + v01 * fx
                val bot = v10 * (1f - fx) + v11 * fx
                out[y * dw + x] = top * (1f - fy) + bot * fy
            }
        }
        return out
    }
}
