package com.alcedo.studio.ui.editor

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.min
import kotlin.math.pow

/**
 * LUT (Look-Up Table) loading and interpolation utilities.
 * Parses .cube LUT files (Adobe Cube format) and provides 3D LUT
 * interpolation for applying colour transforms to images.
 *
 * The .cube format stores a 3D LUT as a flat list of RGB triplets
 * ordered by: R fastest, then G, then B (matching the standard
 * cube file layout: first dimension = R, second = G, third = B).
 */
object LutUtils {

    data class CubeLut(
        val title: String,
        val size: Int,
        val domainMin: FloatArray = floatArrayOf(0f, 0f, 0f),
        val domainMax: FloatArray = floatArrayOf(1f, 1f, 1f),
        /** Flat RGB triplet data, size^3 * 3 floats. */
        val data: FloatArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CubeLut) return false
            return title == other.title && size == other.size && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = title.hashCode()
            result = 31 * result + size
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Parse a .cube LUT file from an [InputStream].
     * Supports LUT_3D_SIZE, TITLE, DOMAIN_MIN, DOMAIN_MAX directives
     * and the table body.
     */
    fun parseCube(inputStream: InputStream): CubeLut {
        var title = ""
        var size = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val data = mutableListOf<Float>()

        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        reader.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

            when {
                trimmed.startsWith("TITLE", ignoreCase = true) -> {
                    title = trimmed.substringAfter("\"").substringBefore("\"")
                }
                trimmed.startsWith("LUT_3D_SIZE", ignoreCase = true) -> {
                    size = trimmed.substringAfter(" ").trim().toIntOrNull() ?: 0
                }
                trimmed.startsWith("DOMAIN_MIN", ignoreCase = true) -> {
                    val parts = trimmed.substringAfter(" ").trim().split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        domainMin = floatArrayOf(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
                    }
                }
                trimmed.startsWith("DOMAIN_MAX", ignoreCase = true) -> {
                    val parts = trimmed.substringAfter(" ").trim().split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        domainMax = floatArrayOf(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
                    }
                }
                else -> {
                    // Data row: R G B
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        parts.take(3).forEach { v ->
                            v.toFloatOrNull()?.let { data.add(it) }
                        }
                    }
                }
            }
        }

        require(size > 0) { "LUT_3D_SIZE not found or invalid" }
        require(data.size >= size * size * size * 3) {
            "Insufficient data: expected ${size * size * size * 3}, got ${data.size}"
        }

        return CubeLut(
            title = title,
            size = size,
            domainMin = domainMin,
            domainMax = domainMax,
            data = data.toFloatArray(),
        )
    }

    /**
     * Apply the 3D LUT to an RGB triple in [0..1] space.
     * Uses trilinear interpolation for smooth results.
     *
     * @param lut The parsed [CubeLut].
     * @param r Red in 0..1.
     * @param g Green in 0..1.
     * @param b Blue in 0..1.
     * @return FloatArray of [r, g, b] after LUT application.
     */
    fun applyLut(lut: CubeLut, r: Float, g: Float, b: Float): FloatArray {
        val n = lut.size - 1
        // Normalize to domain
        val nr = ((r - lut.domainMin[0]) / (lut.domainMax[0] - lut.domainMin[0])).coerceIn(0f, 1f)
        val ng = ((g - lut.domainMin[1]) / (lut.domainMax[1] - lut.domainMin[1])).coerceIn(0f, 1f)
        val nb = ((b - lut.domainMin[2]) / (lut.domainMax[2] - lut.domainMin[2])).coerceIn(0f, 1f)

        // Continuous index
        val ri = nr * n
        val gi = ng * n
        val bi = nb * n

        val r0 = ri.toInt().coerceIn(0, n - 1)
        val g0 = gi.toInt().coerceIn(0, n - 1)
        val b0 = bi.toInt().coerceIn(0, n - 1)
        val r1 = (r0 + 1).coerceAtMost(n)
        val g1 = (g0 + 1).coerceAtMost(n)
        val b1 = (b0 + 1).coerceAtMost(n)

        val rf = ri - r0
        val gf = gi - g0
        val bf = bi - b0

        // Trilinear interpolation over 8 corners
        val result = FloatArray(3)
        for (ch in 0..2) {
            val c000 = sample(lut, r0, g0, b0, ch)
            val c001 = sample(lut, r0, g0, b1, ch)
            val c010 = sample(lut, r0, g1, b0, ch)
            val c011 = sample(lut, r0, g1, b1, ch)
            val c100 = sample(lut, r1, g0, b0, ch)
            val c101 = sample(lut, r1, g0, b1, ch)
            val c110 = sample(lut, r1, g1, b0, ch)
            val c111 = sample(lut, r1, g1, b1, ch)

            val c00 = c000 * (1 - bf) + c001 * bf
            val c01 = c010 * (1 - bf) + c011 * bf
            val c10 = c100 * (1 - bf) + c101 * bf
            val c11 = c110 * (1 - bf) + c111 * bf

            val c0 = c00 * (1 - gf) + c01 * gf
            val c1 = c10 * (1 - gf) + c11 * gf

            result[ch] = c0 * (1 - rf) + c1 * rf
        }
        return result
    }

    /** Sample a single channel from the LUT at integer indices. */
    private fun sample(lut: CubeLut, ri: Int, gi: Int, bi: Int, channel: Int): Float {
        val index = (ri + gi * lut.size + bi * lut.size * lut.size) * 3 + channel
        return lut.data[index.coerceIn(0, lut.data.lastIndex)]
    }

    /**
     * Apply the LUT to an entire bitmap (ARGB_8888).
     * Returns a new bitmap with the LUT applied. For production use
     * this should be done on a background thread.
     */
    fun applyLutToBitmap(lut: CubeLut, bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = ((px shr 16 and 0xFF) / 255f)
            val g = ((px shr 8 and 0xFF) / 255f)
            val b = ((px and 0xFF) / 255f)
            val mapped = applyLut(lut, r, g, b)
            val or = (mapped[0].coerceIn(0f, 1f) * 255).toInt()
            val og = (mapped[1].coerceIn(0f, 1f) * 255).toInt()
            val ob = (mapped[2].coerceIn(0f, 1f) * 255).toInt()
            pixels[i] = (px and 0xFF000000.toInt()) or (or shl 16) or (og shl 8) or ob
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }
}
