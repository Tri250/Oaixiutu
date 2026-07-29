package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Chromaticity diagram (CIE 1931 xy) showing image colour distribution and
 * colour space boundary. Renders the spectral locus outline and plots sampled
 * pixel chromaticities from the preview bitmap.
 */
@Composable
fun ChromaticityView(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    colorSpaceBoundary: List<Offset> = srgbBoundary(),
) {
    val s = Strings.res
    val chromaSamples = remember(bitmap) { computeChromaXY(bitmap) }

    Box(modifier = modifier.background(AlcedoColors.Obsidian)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val margin = 8.dp.toPx()
            val plotW = w - margin * 2
            val plotH = h - margin * 2

            // CIE 1931 axes: x 0..0.8, y 0..0.9
            fun toScreen(x: Float, y: Float): Offset {
                return Offset(
                    margin + (x / 0.8f) * plotW,
                    margin + (1f - y / 0.9f) * plotH,
                )
            }

            // Spectral locus (simplified horseshoe outline)
            val locusPath = Path()
            val spectralLocus = listOf(
                0.175f to 0.005f, 0.174f to 0.015f, 0.174f to 0.060f,
                0.167f to 0.120f, 0.140f to 0.200f, 0.120f to 0.290f,
                0.095f to 0.350f, 0.067f to 0.395f, 0.050f to 0.440f,
                0.040f to 0.490f, 0.030f to 0.535f, 0.020f to 0.580f,
                0.020f to 0.620f, 0.030f to 0.665f, 0.065f to 0.720f,
                0.110f to 0.755f, 0.165f to 0.780f, 0.220f to 0.800f,
                0.280f to 0.805f, 0.340f to 0.790f, 0.400f to 0.760f,
                0.450f to 0.720f, 0.500f to 0.680f, 0.550f to 0.620f,
                0.600f to 0.550f, 0.640f to 0.470f, 0.680f to 0.380f,
                0.700f to 0.300f, 0.720f to 0.230f, 0.730f to 0.170f,
                0.735f to 0.120f, 0.735f to 0.070f, 0.735f to 0.030f,
            )
            spectralLocus.forEachIndexed { i, (x, y) ->
                val pt = toScreen(x, y)
                if (i == 0) locusPath.moveTo(pt.x, pt.y) else locusPath.lineTo(pt.x, pt.y)
            }
            locusPath.close()
            drawPath(
                path = locusPath,
                color = AlcedoColors.TextTertiary.copy(alpha = 0.4f),
                style = Stroke(width = 1.dp.toPx()),
            )

            // Fill the locus with a subtle gradient
            drawPath(
                path = locusPath,
                color = Color.White.copy(alpha = 0.03f),
            )

            // Color space boundary (e.g. sRGB triangle)
            if (colorSpaceBoundary.size >= 3) {
                val boundaryPath = Path()
                colorSpaceBoundary.forEachIndexed { i, pt ->
                    val screenPt = toScreen(pt.x, pt.y)
                    if (i == 0) boundaryPath.moveTo(screenPt.x, screenPt.y)
                    else boundaryPath.lineTo(screenPt.x, screenPt.y)
                }
                boundaryPath.close()
                drawPath(
                    path = boundaryPath,
                    color = AlcedoColors.AccentBlue.copy(alpha = 0.5f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                // Label the primaries
                val labels = listOf("R", "G", "B")
                colorSpaceBoundary.take(3).forEachIndexed { i, pt ->
                    val screenPt = toScreen(pt.x, pt.y)
                    drawCircle(
                        color = AlcedoColors.AccentBlue,
                        radius = 3.dp.toPx(),
                        center = screenPt,
                    )
                }
            }

            // White point (D65)
            val d65 = toScreen(0.3127f, 0.3290f)
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = d65)

            // Plot image chroma samples
            chromaSamples.forEach { (x, y) ->
                val pt = toScreen(x, y)
                drawCircle(
                    color = AlcedoColors.VectorscopeTrace.copy(alpha = 0.3f),
                    radius = 1.dp.toPx(),
                    center = pt,
                )
            }
        }
        Text(
            text = s.chromaticity,
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.align(Alignment.TopStart).padding(DesignTokens.spacingXs),
        )
    }
}

/** sRGB triangle boundary in CIE xy. */
private fun srgbBoundary(): List<Offset> = listOf(
    Offset(0.64f, 0.33f),  // Red
    Offset(0.30f, 0.60f),  // Green
    Offset(0.15f, 0.06f),  // Blue
)

/** Compute CIE xy chromaticity samples from a bitmap. */
private fun computeChromaXY(bitmap: Bitmap?): List<Pair<Float, Float>> {
    if (bitmap == null) return emptyList()
    val samples = mutableListOf<Pair<Float, Float>>()
    val step = (bitmap.width / 48).coerceAtLeast(1)
    val yStep = (bitmap.height / 48).coerceAtLeast(1)
    for (px in 0 until bitmap.width step step) {
        for (py in 0 until bitmap.height step yStep) {
            val pixel = bitmap.getPixel(px, py)
            val rLin = linearize(((pixel shr 16 and 0xFF) / 255f).toDouble())
            val gLin = linearize(((pixel shr 8 and 0xFF) / 255f).toDouble())
            val bLin = linearize(((pixel and 0xFF) / 255f).toDouble())
            // sRGB to XYZ (D65)
            val X = 0.4124564 * rLin + 0.3575761 * gLin + 0.1804375 * bLin
            val Y = 0.2126729 * rLin + 0.7151522 * gLin + 0.0721750 * bLin
            val Z = 0.0193339 * rLin + 0.1191920 * gLin + 0.9503041 * bLin
            val sum = X + Y + Z
            if (sum > 0.0001) {
                val x = (X / sum).toFloat()
                val y = (Y / sum).toFloat()
                if (x in 0f..1f && y in 0f..1f) {
                    samples += x to y
                }
            }
        }
    }
    return samples
}

private fun linearize(c: Double): Double = if (c <= 0.04045) c / 12.92 else pow((c + 0.055) / 1.055, 2.4)
