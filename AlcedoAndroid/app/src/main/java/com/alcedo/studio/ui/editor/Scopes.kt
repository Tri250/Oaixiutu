package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Histogram scope. Renders an RGB+luminance histogram computed from a preview
 * bitmap. When [bitmap] is null a flat baseline is drawn so the panel keeps its
 * layout during render.
 */
@Composable
fun HistogramView(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val histogram = remember(bitmap) { computeHistogram(bitmap) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(DesignTokens.histogramHeight)
            .background(AlcedoColors.Obsidian),
    ) {
        val w = size.width
        val h = size.height
        val maxVal = histogram.flatMap { it.asList() }.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
        val channels = listOf(
            histogram[0] to AlcedoColors.ChannelRed.copy(alpha = 0.7f),
            histogram[1] to AlcedoColors.ChannelGreen.copy(alpha = 0.7f),
            histogram[2] to AlcedoColors.ChannelBlue.copy(alpha = 0.7f),
        )
        channels.forEach { (data, color) ->
            val path = Path()
            data.forEachIndexed { i, value ->
                val x = (i / 255f) * w
                val y = h - (value.toFloat() / maxVal) * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 1.dp.toPx()))
        }
    }
}

/**
 * Waveform/parade scope. Renders a luminance column-waveform derived from the
 * preview bitmap. Each column x maps to a horizontal slice of the image; the
 * intensity at y represents how many pixels in that column have that luma.
 */
@Composable
fun WaveformScope(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Box(modifier = modifier.background(AlcedoColors.Obsidian)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (bitmap == null) {
                drawLine(AlcedoColors.Divider, Offset(0f, h / 2f), Offset(w, h / 2f), 1.dp.toPx())
                return@Canvas
            }
            // Sample columns for performance.
            val cols = 96
            val sampleStep = (bitmap.width / cols).coerceAtLeast(1)
            for (cx in 0 until cols) {
                val px = (cx * sampleStep).coerceAtMost(bitmap.width - 1)
                var lumaAccum = 0f
                var count = 0
                val yStep = (bitmap.height / 48).coerceAtLeast(1)
                for (py in 0 until bitmap.height step yStep) {
                    val pixel = bitmap.getPixel(px, py)
                    val r = (pixel shr 16 and 0xFF) / 255f
                    val g = (pixel shr 8 and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    lumaAccum += 0.2126f * r + 0.7152f * g + 0.0722f * b
                    count++
                }
                val luma = (lumaAccum / count.coerceAtLeast(1))
                val xPos = (cx / cols.toFloat()) * w
                val yPos = h - luma * h
                drawCircle(
                    color = AlcedoColors.LumaTrace.copy(alpha = 0.5f),
                    radius = 1.dp.toPx(),
                    center = Offset(xPos, yPos),
                )
            }
        }
        Text(
            text = s.waveform,
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.align(Alignment.TopStart).padding(DesignTokens.spacingXs),
        )
    }
}

/** Compute 3×256 RGB histogram bins from a (downsampled) bitmap. */
private fun computeHistogram(bitmap: Bitmap?): Array<IntArray> {
    val bins = Array(3) { IntArray(256) }
    if (bitmap == null) return bins
    val step = (bitmap.width / 128).coerceAtLeast(1)
    val yStep = (bitmap.height / 128).coerceAtLeast(1)
    for (x in 0 until bitmap.width step step) {
        for (y in 0 until bitmap.height step yStep) {
            val pixel = bitmap.getPixel(x, y)
            bins[0][(pixel shr 16 and 0xFF).coerceIn(0, 255)]++
            bins[1][(pixel shr 8 and 0xFF).coerceIn(0, 255)]++
            bins[2][(pixel and 0xFF).coerceIn(0, 255)]++
        }
    }
    return bins
}
