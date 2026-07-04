package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

enum class WaveformMode(val label: String) {
    RGB_PARADE("RGB Parade"),
    OVERLAY("Overlay"),
    LUMINANCE("Luminance")
}

@Composable
fun WaveformView(
    waveformData: WaveformData,
    modifier: Modifier = Modifier,
    mode: WaveformMode = WaveformMode.RGB_PARADE,
    backgroundColor: Color = Color(0xFF1A1A1A)
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val w = size.width
            val h = size.height
            val padding = 4f

            // Background
            drawRect(color = backgroundColor, size = size)

            when (mode) {
                WaveformMode.RGB_PARADE -> {
                    val paradeWidth = (w - 2 * padding) / 3f
                    val paradeHeight = h - 2 * padding

                    drawWaveformChannel2D(
                        data = waveformData,
                        channel = { it.r },
                        color = Color.Red,
                        rect = Rect(
                            padding, padding,
                            padding + paradeWidth, padding + paradeHeight
                        ),
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    drawWaveformChannel2D(
                        data = waveformData,
                        channel = { it.g },
                        color = Color.Green,
                        rect = Rect(
                            padding + paradeWidth, padding,
                            padding + 2 * paradeWidth, padding + paradeHeight
                        ),
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    drawWaveformChannel2D(
                        data = waveformData,
                        channel = { it.b },
                        color = Color(0xFF4488FF),
                        rect = Rect(
                            padding + 2 * paradeWidth, padding,
                            padding + 3 * paradeWidth, padding + paradeHeight
                        ),
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                }
                WaveformMode.OVERLAY -> {
                    val drawRect = Rect(padding, padding, w - padding, h - padding)
                    drawWaveformChannel2D(
                        data = waveformData,
                        channel = { it.r },
                        color = Color.Red.copy(alpha = 0.5f),
                        rect = drawRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    drawWaveformChannel2D(
                        data = waveformData,
                        channel = { it.g },
                        color = Color.Green.copy(alpha = 0.5f),
                        rect = drawRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                    drawWaveformChannel2D(
                        data = waveformData,
                        channel = { it.b },
                        color = Color(0xFF4488FF).copy(alpha = 0.5f),
                        rect = drawRect,
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                }
                WaveformMode.LUMINANCE -> {
                    drawWaveformChannel2D(
                        data = waveformData,
                        channel = { it.luminance },
                        color = Color.White,
                        rect = Rect(padding, padding, w - padding, h - padding),
                        dataCols = waveformData.columns,
                        dataRows = waveformData.rows
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            WaveformMode.entries.forEach { m ->
                Text(
                    text = m.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mode == m) Color.White else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * Draw a 2D waveform: x = column position, y = brightness level,
 * pixel brightness = data intensity (frequency).
 */
private fun DrawScope.drawWaveformChannel2D(
    data: WaveformData,
    channel: (WaveformData) -> FloatArray,
    color: Color,
    rect: Rect,
    dataCols: Int,
    dataRows: Int
) {
    if (dataCols <= 0 || dataRows <= 0) return
    val channelData = channel(data)
    if (channelData.isEmpty()) return

    val graphW = rect.width
    val graphH = rect.height

    // IRE grid lines
    val ireColor = Color.White.copy(alpha = 0.06f)
    for (i in 1..9) {
        val y = rect.top + graphH * i / 10f
        drawLine(
            color = ireColor,
            start = Offset(rect.left, y),
            end = Offset(rect.right, y),
            strokeWidth = 0.5f
        )
    }

    // Draw waveform pixels
    val cellW = graphW / dataCols
    val cellH = graphH / dataRows

    for (col in 0 until dataCols) {
        for (row in 0 until dataRows) {
            val intensity = channelData[col * dataRows + row]
            if (intensity > 0.02f) {
                val x = rect.left + col * cellW
                val y = rect.top + row * cellH
                drawRect(
                    color = color.copy(alpha = intensity.coerceIn(0f, 1f) * 0.8f),
                    topLeft = Offset(x, y),
                    size = Size(cellW.coerceAtLeast(1f), cellH.coerceAtLeast(1f))
                )
            }
        }
    }

    // Border
    drawRect(
        color = Color.White.copy(alpha = 0.15f),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 1f)
    )
}
