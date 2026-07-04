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

data class HistogramData(
    val red: List<Int> = emptyList(),
    val green: List<Int> = emptyList(),
    val blue: List<Int> = emptyList(),
    val luminance: List<Int> = emptyList()
)

@Composable
fun HistogramView(
    histogramData: HistogramData,
    modifier: Modifier = Modifier,
    showChannels: HistogramChannel = HistogramChannel.RGB,
    showClippingIndicators: Boolean = true,
    shadowClipThreshold: Int = 5,
    highlightClipThreshold: Int = 250
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val w = size.width
            val h = size.height
            val padding = 4f
            val graphW = w - 2 * padding
            val graphH = h - 2 * padding

            // Background
            drawRect(
                color = Color(0xFF1A1A1A),
                topLeft = Offset(padding, padding),
                size = Size(graphW, graphH)
            )

            val maxCount = maxOf(
                histogramData.red.maxOrNull() ?: 1,
                histogramData.green.maxOrNull() ?: 1,
                histogramData.blue.maxOrNull() ?: 1,
                histogramData.luminance.maxOrNull() ?: 1
            ).toFloat().coerceAtLeast(1f)

            fun drawChannel(data: List<Int>, color: Color, alpha: Float = 0.7f) {
                if (data.isEmpty()) return
                val barWidth = graphW / data.size
                for (i in data.indices) {
                    val barHeight = (data[i].toFloat() / maxCount) * graphH
                    drawRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(padding + i * barWidth, padding + graphH - barHeight),
                        size = Size(barWidth.coerceAtLeast(1f), barHeight)
                    )
                }
            }

            when (showChannels) {
                HistogramChannel.RGB -> {
                    drawChannel(histogramData.red, Color.Red, 0.5f)
                    drawChannel(histogramData.green, Color.Green, 0.5f)
                    drawChannel(histogramData.blue, Color(0xFF4488FF), 0.5f)
                }
                HistogramChannel.RED -> drawChannel(histogramData.red, Color.Red, 0.8f)
                HistogramChannel.GREEN -> drawChannel(histogramData.green, Color.Green, 0.8f)
                HistogramChannel.BLUE -> drawChannel(histogramData.blue, Color(0xFF4488FF), 0.8f)
                HistogramChannel.LUMINANCE -> drawChannel(histogramData.luminance, Color.White, 0.8f)
            }

            // Clipping indicators
            if (showClippingIndicators) {
                // Shadow clipping (left edge)
                drawRect(
                    color = Color(0xFF2979FF).copy(alpha = 0.6f),
                    topLeft = Offset(padding, padding),
                    size = Size(4f, graphH)
                )
                // Highlight clipping (right edge)
                drawRect(
                    color = Color(0xFFFF1744).copy(alpha = 0.6f),
                    topLeft = Offset(padding + graphW - 4f, padding),
                    size = Size(4f, graphH)
                )
            }

            // Border
            drawRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = Offset(padding, padding),
                size = Size(graphW, graphH),
                style = Stroke(width = 1f)
            )
        }

        // Channel labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistogramChannel.entries.forEach { ch ->
                val isSelected = showChannels == ch
                val color = when (ch) {
                    HistogramChannel.RGB -> Color.White
                    HistogramChannel.RED -> Color.Red
                    HistogramChannel.GREEN -> Color.Green
                    HistogramChannel.BLUE -> Color(0xFF4488FF)
                    HistogramChannel.LUMINANCE -> Color.White
                }
                Text(
                    text = ch.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) color else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

enum class HistogramChannel(val label: String) {
    RGB("RGB"),
    RED("R"),
    GREEN("G"),
    BLUE("B"),
    LUMINANCE("L")
}