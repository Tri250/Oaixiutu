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
 * Waveform scope display (RGB parade). Draws luminance distribution per image
 * column, brightness-coded. Each column x maps to a horizontal slice of the
 * image; intensity at y represents how many pixels in that column have that luma.
 * Supports separate R/G/B channels or combined parade mode.
 */
@Composable
fun WaveformView(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    paradeMode: Boolean = true,
) {
    val s = Strings.res
    val waveform = remember(bitmap) { computeWaveformData(bitmap) }

    Box(modifier = modifier.background(AlcedoColors.Obsidian)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (waveform.isEmpty()) {
                drawLine(AlcedoColors.Divider, Offset(0f, h / 2f), Offset(w, h / 2f), 1.dp.toPx())
                return@Canvas
            }

            if (paradeMode) {
                // RGB Parade: three side-by-side waveforms
                val thirdW = w / 3f
                val channels = listOf(
                    Triple(waveform, AlcedoColors.ChannelRed, 0),
                    Triple(waveform, AlcedoColors.ChannelGreen, 1),
                    Triple(waveform, AlcedoColors.ChannelBlue, 2),
                )
                channels.forEach { (data, color, chIdx) ->
                    val xOffset = chIdx * thirdW
                    data.forEach { col ->
                        val xPos = xOffset + (col.columnIndex.toFloat() / data.size.coerceAtLeast(1)) * thirdW
                        when (chIdx) {
                            0 -> {
                                val yPos = h - col.redLuma * h
                                drawCircle(
                                    color = color.copy(alpha = 0.6f),
                                    radius = 1.dp.toPx(),
                                    center = Offset(xPos, yPos),
                                )
                            }
                            1 -> {
                                val yPos = h - col.greenLuma * h
                                drawCircle(
                                    color = color.copy(alpha = 0.6f),
                                    radius = 1.dp.toPx(),
                                    center = Offset(xPos, yPos),
                                )
                            }
                            2 -> {
                                val yPos = h - col.blueLuma * h
                                drawCircle(
                                    color = color.copy(alpha = 0.6f),
                                    radius = 1.dp.toPx(),
                                    center = Offset(xPos, yPos),
                                )
                            }
                        }
                    }
                }
                // Channel separators
                drawLine(AlcedoColors.Divider, Offset(thirdW, 0f), Offset(thirdW, h), 1.dp.toPx())
                drawLine(AlcedoColors.Divider, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, h), 1.dp.toPx())
            } else {
                // Combined waveform
                data class LumaColumn(val x: Float, val luma: Float, val r: Float, val g: Float, val b: Float)
                val combined = waveform.map { col ->
                    val xPos = (col.columnIndex.toFloat() / waveform.size.coerceAtLeast(1)) * w
                    val luma = (col.redLuma * 0.2126f + col.greenLuma * 0.7152f + col.blueLuma * 0.0722f)
                    LumaColumn(xPos, luma, col.redLuma, col.greenLuma, col.blueLuma)
                }
                combined.forEach { col ->
                    val yPos = h - col.luma * h
                    drawCircle(
                        color = AlcedoColors.LumaTrace.copy(alpha = 0.5f),
                        radius = 1.dp.toPx(),
                        center = Offset(col.x, yPos),
                    )
                }
            }

            // Horizontal reference lines
            for (i in 1..3) {
                val y = h * i / 4f
                drawLine(AlcedoColors.Divider.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), 1.dp.toPx())
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

private data class WaveformColumn(
    val columnIndex: Int,
    val redLuma: Float,
    val greenLuma: Float,
    val blueLuma: Float,
)

private fun computeWaveformData(bitmap: Bitmap?): List<WaveformColumn> {
    if (bitmap == null) return emptyList()
    val cols = 128
    val result = mutableListOf<WaveformColumn>()
    val sampleStepX = (bitmap.width / cols).coerceAtLeast(1)
    val sampleStepY = (bitmap.height / 64).coerceAtLeast(1)

    for (cx in 0 until cols) {
        val px = (cx * sampleStepX).coerceAtMost(bitmap.width - 1)
        var rAccum = 0f
        var gAccum = 0f
        var bAccum = 0f
        var count = 0
        for (py in 0 until bitmap.height step sampleStepY) {
            val pixel = bitmap.getPixel(px, py)
            rAccum += (pixel shr 16 and 0xFF) / 255f
            gAccum += (pixel shr 8 and 0xFF) / 255f
            bAccum += (pixel and 0xFF) / 255f
            count++
        }
        val n = count.coerceAtLeast(1)
        result += WaveformColumn(cx, rAccum / n, gAccum / n, bAccum / n)
    }
    return result
}
