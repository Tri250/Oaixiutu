package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

data class WaveformData(
    val red: List<Float> = emptyList(),
    val green: List<Float> = emptyList(),
    val blue: List<Float> = emptyList(),
    val luminance: List<Float> = emptyList()
)

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

                    drawWaveformChannel(
                        data = waveformData.red,
                        color = Color.Red,
                        rect = Rect(
                            padding, padding,
                            padding + paradeWidth, padding + paradeHeight
                        )
                    )
                    drawWaveformChannel(
                        data = waveformData.green,
                        color = Color.Green,
                        rect = Rect(
                            padding + paradeWidth, padding,
                            padding + 2 * paradeWidth, padding + paradeHeight
                        )
                    )
                    drawWaveformChannel(
                        data = waveformData.blue,
                        color = Color(0xFF4488FF),
                        rect = Rect(
                            padding + 2 * paradeWidth, padding,
                            padding + 3 * paradeWidth, padding + paradeHeight
                        )
                    )
                }
                WaveformMode.LUMINANCE -> {
                    drawWaveformChannel(
                        data = waveformData.luminance,
                        color = Color.White,
                        rect = Rect(padding, padding, w - padding, h - padding)
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

private fun DrawScope.drawWaveformChannel(
    data: List<Float>,
    color: Color,
    rect: Rect
) {
    if (data.isEmpty()) return

    val graphW = rect.width
    val graphH = rect.height

    // Draw IRE grid lines
    val ireColor = Color.White.copy(alpha = 0.08f)
    for (i in 1..9) {
        val y = rect.top + graphH * i / 10f
        drawLine(
            color = ireColor,
            start = Offset(rect.left, y),
            end = Offset(rect.right, y),
            strokeWidth = 0.5f
        )
    }

    // Draw waveform
    val barWidth = graphW / data.size
    for (i in data.indices) {
        val intensity = data[i].coerceIn(0f, 1f)
        if (intensity > 0.001f) {
            val barHeight = intensity * graphH
            drawRect(
                color = color.copy(alpha = 0.6f),
                topLeft = Offset(rect.left + i * barWidth, rect.top + graphH - barHeight),
                size = Size(barWidth.coerceAtLeast(1f), barHeight)
            )
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

enum class WaveformMode(val label: String) {
    RGB_PARADE("RGB Parade"),
    LUMINANCE("Luminance")
}