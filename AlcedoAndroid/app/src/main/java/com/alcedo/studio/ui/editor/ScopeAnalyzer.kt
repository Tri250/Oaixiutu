package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.pow

// ── Data classes ──────────────────────────────────────────────────────────

data class HistogramData(
    val r: FloatArray = FloatArray(256),
    val g: FloatArray = FloatArray(256),
    val b: FloatArray = FloatArray(256),
    val luminance: FloatArray = FloatArray(256)
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/** Waveform: 2-D intensity map per channel. columns × rows, normalized 0..1. */
data class WaveformData(
    val columns: Int = 0,
    val rows: Int = 192,
    val r: FloatArray = FloatArray(0),
    val g: FloatArray = FloatArray(0),
    val b: FloatArray = FloatArray(0),
    val luminance: FloatArray = FloatArray(0)
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/**
 * Vectorscope data: binned 256×256 accumulator for Cb/Cr chrominance.
 * Each element is a count; callers normalize as needed.
 */
data class VectorscopeData(
    val size: Int = 256,
    val bins: IntArray = IntArray(256 * 256)
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/**
 * Chromaticity data: list of (x, y) chromaticity coordinates sampled from
 * the image, plus optional per-point brightness for alpha modulation.
 */
data class ChromaticityData(
    val points: List<ChromaticityPoint> = emptyList()
)

data class ChromaticityPoint(
    val x: Float,     // CIE x
    val y: Float,     // CIE y
    val brightness: Float // 0..1 luminance fraction
)

// ── Analyzer ──────────────────────────────────────────────────────────────

object ScopeAnalyzer {

    // ── Histogram ──────────────────────────────────────────────────────

    suspend fun computeHistogram(bitmap: Bitmap?): HistogramData =
        withContext(Dispatchers.Default) {
            if (bitmap == null) return@withContext HistogramData()

            val r = FloatArray(256)
            val g = FloatArray(256)
            val b = FloatArray(256)
            val lum = FloatArray(256)

            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            var rMax = 0f
            var gMax = 0f
            var bMax = 0f
            var lMax = 0f

            for (pixel in pixels) {
                val rv = (pixel shr 16) and 0xFF
                val gv = (pixel shr 8) and 0xFF
                val bv = pixel and 0xFF
                val lv = (rv * 77 + gv * 150 + bv * 29) shr 8   // REC.601 luma

                r[rv] += 1f
                g[gv] += 1f
                b[bv] += 1f
                lum[lv] += 1f

                if (r[rv] > rMax) rMax = r[rv]
                if (g[gv] > gMax) gMax = g[gv]
                if (b[bv] > bMax) bMax = b[bv]
                if (lum[lv] > lMax) lMax = lum[lv]
            }

            // Normalize to 0..1
            if (rMax > 0f) for (i in 0..255) r[i] /= rMax
            if (gMax > 0f) for (i in 0..255) g[i] /= gMax
            if (bMax > 0f) for (i in 0..255) b[i] /= bMax
            if (lMax > 0f) for (i in 0..255) lum[i] /= lMax

            HistogramData(r = r, g = g, b = b, luminance = lum)
        }

    // ── Waveform ───────────────────────────────────────────────────────

    suspend fun computeWaveform(
        bitmap: Bitmap?,
        targetColumns: Int = 384,
        targetRows: Int = 192
    ): WaveformData = withContext(Dispatchers.Default) {
        if (bitmap == null) return@withContext WaveformData()

        val srcW = bitmap.width
        val srcH = bitmap.height
        val pixels = IntArray(srcW * srcH)
        bitmap.getPixels(pixels, 0, srcW, 0, 0, srcW, srcH)

        val cols = targetColumns
        val rows = targetRows
        val r = FloatArray(cols * rows)
        val g = FloatArray(cols * rows)
        val b = FloatArray(cols * rows)
        val lum = FloatArray(cols * rows)

        // For each output column, find the source column range
        for (col in 0 until cols) {
            val srcStart = (col * srcW) / cols
            val srcEnd = ((col + 1) * srcW) / cols

            // Accumulate brightness histogram for this strip
            val stripR = IntArray(rows)
            val stripG = IntArray(rows)
            val stripB = IntArray(rows)
            val stripL = IntArray(rows)

            var count = 0
            for (sx in srcStart until srcEnd) {
                for (sy in 0 until srcH) {
                    val pixel = pixels[sy * srcW + sx]
                    val rv = (pixel shr 16) and 0xFF
                    val gv = (pixel shr 8) and 0xFF
                    val bv = pixel and 0xFF
                    val lv = (rv * 77 + gv * 150 + bv * 29) shr 8

                    // Map value (0..255) to row (top=bright, bottom=dark)
                    val rowR = ((255 - rv) * (rows - 1)) / 255
                    val rowG = ((255 - gv) * (rows - 1)) / 255
                    val rowB = ((255 - bv) * (rows - 1)) / 255
                    val rowL = ((255 - lv) * (rows - 1)) / 255

                    stripR[rowR]++
                    stripG[rowG]++
                    stripB[rowB]++
                    stripL[rowL]++
                    count++
                }
            }

            if (count == 0) continue

            // Find max for normalization
            var maxR = 0; var maxG = 0; var maxB = 0; var maxL = 0
            for (row in 0 until rows) {
                if (stripR[row] > maxR) maxR = stripR[row]
                if (stripG[row] > maxG) maxG = stripG[row]
                if (stripB[row] > maxB) maxB = stripB[row]
                if (stripL[row] > maxL) maxL = stripL[row]
            }

            for (row in 0 until rows) {
                val idx = col * rows + row
                r[idx] = if (maxR > 0) stripR[row].toFloat() / maxR else 0f
                g[idx] = if (maxG > 0) stripG[row].toFloat() / maxG else 0f
                b[idx] = if (maxB > 0) stripB[row].toFloat() / maxB else 0f
                lum[idx] = if (maxL > 0) stripL[row].toFloat() / maxL else 0f
            }
        }

        WaveformData(columns = cols, rows = rows, r = r, g = g, b = b, luminance = lum)
    }

    // ── Vectorscope ────────────────────────────────────────────────────

    suspend fun computeVectorscope(
        bitmap: Bitmap?,
        size: Int = 256
    ): VectorscopeData = withContext(Dispatchers.Default) {
        if (bitmap == null) return@withContext VectorscopeData()

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val bins = IntArray(size * size)

        // Step size for sampling (skip pixels on large images)
        val step = max(1, (w * h) / (size * size * 4))

        var idx = 0
        while (idx < pixels.size) {
            val pixel = pixels[idx]
            val rv = ((pixel shr 16) and 0xFF) / 255f
            val gv = ((pixel shr 8) and 0xFF) / 255f
            val bv = (pixel and 0xFF) / 255f

            // RGB → YCbCr (ITU-R BT.601)
            val y  =  0.299f * rv + 0.587f * gv + 0.114f * bv
            val cb = -0.169f * rv - 0.331f * gv + 0.500f * bv + 0.5f
            val cr =  0.500f * rv - 0.419f * gv - 0.081f * bv + 0.5f

            // Map Cb,Cr [0,1] → bin index [0, size-1]
            val bx = (cb * (size - 1)).toInt().coerceIn(0, size - 1)
            val by = (cr * (size - 1)).toInt().coerceIn(0, size - 1)
            bins[by * size + bx]++

            idx += step
        }

        VectorscopeData(size = size, bins = bins)
    }

    // ── Chromaticity ───────────────────────────────────────────────────

    suspend fun computeChromaticity(
        bitmap: Bitmap?,
        maxPoints: Int = 8192
    ): ChromaticityData = withContext(Dispatchers.Default) {
        if (bitmap == null) return@withContext ChromaticityData()

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val totalPixels = w * h
        val step = max(1, totalPixels / maxPoints)
        val points = mutableListOf<ChromaticityPoint>()

        var idx = 0
        while (idx < pixels.size && points.size < maxPoints) {
            val pixel = pixels[idx]
            val rv = ((pixel shr 16) and 0xFF) / 255f
            val gv = ((pixel shr 8) and 0xFF) / 255f
            val bv = (pixel and 0xFF) / 255f

            // sRGB → linear
            val lr = srgbToLinear(rv)
            val lg = srgbToLinear(gv)
            val lb = srgbToLinear(bv)

            // Linear RGB → XYZ (sRGB D65)
            val x = 0.4124564f * lr + 0.3575761f * lg + 0.1804375f * lb
            val y = 0.2126729f * lr + 0.7151522f * lg + 0.0721750f * lb
            val z = 0.0193339f * lr + 0.1191920f * lg + 0.9503041f * lb

            val sum = x + y + z
            if (sum > 1e-6f) {
                val cx = x / sum
                val cy = y / sum
                points.add(ChromaticityPoint(x = cx, y = cy, brightness = y))
            }

            idx += step
        }

        ChromaticityData(points = points)
    }

    // ── sRGB ↔ Linear helpers ──────────────────────────────────────────

    fun srgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) c / 12.92f
        else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }

    fun linearToSrgb(c: Float): Float {
        return if (c <= 0.0031308f) 12.92f * c
        else (1.055f * c.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()
    }

    // ── CIE xy → sRGB (for drawing spectral locus colours) ─────────────

    private val sRgbToXyz = floatArrayOf(
         0.4124564f, 0.3575761f, 0.1804375f,
         0.2126729f, 0.7151522f, 0.0721750f,
         0.0193339f, 0.1191920f, 0.9503041f
    )

    private val xyzToSRgb = floatArrayOf(
         3.2404542f, -1.5371385f, -0.4985314f,
        -0.9692660f,  1.8760108f,  0.0415560f,
         0.0556434f, -0.2040259f,  1.0572252f
    )

    /** Convert a CIE xy chromaticity (at Y=1) to linear sRGB. */
    fun xyToLinearRGB(cx: Float, cy: Float): FloatArray {
        val x = cx
        val y = cy
        val z = 1f - x - y
        if (y <= 0f) return floatArrayOf(0f, 0f, 0f)
        val X = x / y
        val Y = 1f
        val Z = z / y

        val r = xyzToSRgb[0] * X + xyzToSRgb[1] * Y + xyzToSRgb[2] * Z
        val g = xyzToSRgb[3] * X + xyzToSRgb[4] * Y + xyzToSRgb[5] * Z
        val b = xyzToSRgb[6] * X + xyzToSRgb[7] * Y + xyzToSRgb[8] * Z

        return floatArrayOf(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    // ── Spectral locus (CIE 1931 2° observer, D65) ────────────────────

    val SPECTRAL_LOCUS: List<Pair<Float, Float>> by lazy {
        listOf(
            0.1744f to 0.0050f,
            0.1743f to 0.0052f,
            0.1740f to 0.0056f,
            0.1734f to 0.0062f,
            0.1726f to 0.0070f,
            0.1714f to 0.0080f,
            0.1699f to 0.0092f,
            0.1680f to 0.0108f,
            0.1655f to 0.0128f,
            0.1624f to 0.0152f,
            0.1587f to 0.0180f,
            0.1545f to 0.0212f,
            0.1499f to 0.0250f,
            0.1449f to 0.0294f,
            0.1396f to 0.0346f,
            0.1340f to 0.0408f,
            0.1283f to 0.0480f,
            0.1225f to 0.0562f,
            0.1167f to 0.0656f,
            0.1110f to 0.0762f,
            0.1054f to 0.0882f,
            0.1000f to 0.1012f,
            0.0947f to 0.1156f,
            0.0895f to 0.1312f,
            0.0845f to 0.1480f,
            0.0797f to 0.1660f,
            0.0750f to 0.1852f,
            0.0706f to 0.2052f,
            0.0664f to 0.2260f,
            0.0624f to 0.2476f,
            0.0587f to 0.2700f,
            0.0552f to 0.2932f,
            0.0519f to 0.3172f,
            0.0489f to 0.3420f,
            0.0460f to 0.3676f,
            0.0434f to 0.3940f,
            0.0409f to 0.4212f,
            0.0386f to 0.4492f,
            0.0365f to 0.4776f,
            0.0346f to 0.5068f,
            0.0329f to 0.5364f,
            0.0314f to 0.5664f,
            0.0300f to 0.5968f,
            0.0288f to 0.6276f,
            0.0277f to 0.6584f,
            0.0268f to 0.6896f,
            0.0259f to 0.7208f,
            0.0252f to 0.7520f,
            0.0245f to 0.7832f,
            0.0240f to 0.8140f,
            0.0235f to 0.8448f,
            0.0231f to 0.8752f,
            0.0227f to 0.9052f,
            0.0224f to 0.9348f,
            0.0221f to 0.9640f,
            0.0218f to 0.9928f,
            0.0216f to 1.0212f,
            0.0214f to 1.0492f,
            0.0211f to 1.0768f,
            0.0209f to 1.1040f,
            0.0207f to 1.1308f,
            0.0205f to 1.1572f,
            0.0203f to 1.1832f,
            0.0202f to 1.2088f,
            0.0200f to 1.2340f,
            0.0199f to 1.2588f,
            0.0197f to 1.2832f,
            0.0196f to 1.3072f,
            0.0195f to 1.3308f,
            0.0194f to 1.3540f,
            0.0193f to 1.3768f,
            0.0192f to 1.3992f,
            0.0191f to 1.4212f,
            0.0190f to 1.4428f,
            0.0189f to 1.4640f,
            0.0189f to 1.4848f,
            0.0188f to 1.5052f,
            0.0187f to 1.5252f,
            0.0187f to 1.5448f,
            0.0186f to 1.5640f,
            0.0186f to 1.5828f
        )
    }

    // ── Color-space gamut vertices (CIE xy at Y=1) ────────────────────

    val SRGB_GAMUT = listOf(
        0.64f to 0.33f,   // R
        0.30f to 0.60f,   // G
        0.15f to 0.06f    // B
    )

    val P3_GAMUT = listOf(
        0.680f to 0.320f,  // R
        0.265f to 0.690f,  // G
        0.150f to 0.060f   // B
    )

    val REC2020_GAMUT = listOf(
        0.708f to 0.292f,  // R
        0.170f to 0.797f,  // G
        0.131f to 0.046f   // B
    )

    val ACES_GAMUT = listOf(
        0.7347f to 0.2653f,  // R
        0.0000f to 1.0000f,  // G
        0.0001f to -0.077f   // B
    )

    // ── Vectorscope colour targets ─────────────────────────────────────

    /** Primary/secondary colour Cb/Cr positions in [0,1] space (derived from BT.601). */
    val VECTORSCOPE_TARGETS = mapOf(
        "R" to (0.5f to 1.0f),
        "G" to (0.168f to 0.081f),
        "B" to (1.0f to 0.5f),
        "C" to (0.5f to 0.0f),
        "M" to (0.832f to 0.919f),
        "Y" to (0.0f to 0.5f)
    )

    /** Skin-tone line in vectorscope Cb/Cr space (approximate). */
    val SKIN_TONE_LINE = Pair(
        0.44f to 0.52f,  // light skin
        0.42f to 0.56f   // dark skin
    )
}
