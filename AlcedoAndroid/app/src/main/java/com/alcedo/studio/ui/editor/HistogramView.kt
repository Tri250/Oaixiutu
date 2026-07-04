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
import kotlin.math.log10

enum class HistogramChannel(val label: String) {
    RGB("RGB"),
    RED("R"),
    GREEN("G"),
    BLUE("B"),
    LUMINANCE("L")
}

enum class HistogramScale(val label: String) {
    LINEAR("Linear"),
    LOGARITHMIC("Log")
}

@Composable
fun HistogramView(
    histogramData: HistogramData,
    modifier: Modifier = Modifier,
    showChannels: HistogramChannel = HistogramChannel.RGB,
    scale: HistogramScale = HistogramScale.LINEAR,
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

            val maxR = histogramData.r.maxOrNull() ?: 0f
            val maxG = histogramData.g.maxOrNull() ?: 0f
            val maxB = histogramData.b.maxOrNull() ?: 0f
            val maxL = histogramData.luminance.maxOrNull() ?: 0f
            val maxCount = maxOf(maxR, maxG, maxB, maxL).coerceAtLeast(1f)

            val logMax = if (maxCount > 0f) log10(maxCount.toDouble()).toFloat() else 1f

            fun valueToHeight(value: Float): Float {
                if (value <= 0f) return 0f
                return when (scale) {
                    HistogramScale.LINEAR -> (value / maxCount) * graphH
                    HistogramScale.LOGARITHMIC -> {
                        val logVal = log10(value.toDouble()).toFloat()
                        (logVal / logMax.coerceAtLeast(0.001f)) * graphH
                    }
                }
            }

            fun drawChannelFill(data: FloatArray, color: Color, alpha: Float = 0.7f) {
                if (data.isEmpty()) return
                val barWidth = graphW / data.size
                val path = Path()
                path.moveTo(padding, padding + graphH)
                for (i in data.indices) {
                    val barHeight = valueToHeight(data[i])
                    val x = padding + i * barWidth
                    val y = padding + graphH - barHeight
                    if (i == 0) path.lineTo(x, y) else path.lineTo(x, y)
                }
                path.lineTo(padding + data.size * barWidth, padding + graphH)
                path.close()
                drawPath(path, color.copy(alpha = alpha))
            }

            fun drawChannelLine(data: FloatArray, color: Color, alpha: Float = 0.8f) {
                if (data.isEmpty()) return
                val barWidth = graphW / data.size
                for (i in 0 until data.size - 1) {
                    val h1 = valueToHeight(data[i])
                    val h2 = valueToHeight(data[i + 1])
                    drawLine(
                        color = color.copy(alpha = alpha),
                        start = Offset(padding + i * barWidth, padding + graphH - h1),
                        end = Offset(padding + (i + 1) * barWidth, padding + graphH - h2),
                        strokeWidth = 1f
                    )
                }
            }

            when (showChannels) {
                HistogramChannel.RGB -> {
                    drawChannelFill(histogramData.r, Color.Red, 0.35f)
                    drawChannelFill(histogramData.g, Color.Green, 0.30f)
                    drawChannelFill(histogramData.b, Color(0xFF4488FF), 0.30f)
                    drawChannelLine(histogramData.r, Color.Red, 0.5f)
                    drawChannelLine(histogramData.g, Color.Green, 0.45f)
                    drawChannelLine(histogramData.b, Color(0xFF4488FF), 0.45f)
                }
                HistogramChannel.RED -> {
                    drawChannelFill(histogramData.r, Color.Red, 0.5f)
                    drawChannelLine(histogramData.r, Color.Red, 0.8f)
                }
                HistogramChannel.GREEN -> {
                    drawChannelFill(histogramData.g, Color.Green, 0.5f)
                    drawChannelLine(histogramData.g, Color.Green, 0.8f)
                }
                HistogramChannel.BLUE -> {
                    drawChannelFill(histogramData.b, Color(0xFF4488FF), 0.5f)
                    drawChannelLine(histogramData.b, Color(0xFF4488FF), 0.8f)
                }
                HistogramChannel.LUMINANCE -> {
                    drawChannelFill(histogramData.luminance, Color.White, 0.4f)
                    drawChannelLine(histogramData.luminance, Color.White, 0.7f)
                }
            }

            // Clipping indicators
            if (showClippingIndicators) {
                val shadowX = padding + (shadowClipThreshold / 255f) * graphW
                val highlightX = padding + (highlightClipThreshold / 255f) * graphW
                drawRect(
                    color = Color(0xFF2979FF).copy(alpha = 0.3f),
                    topLeft = Offset(padding, padding),
                    size = Size(shadowX - padding, graphH)
                )
                drawRect(
                    color = Color(0xFFFF1744).copy(alpha = 0.3f),
                    topLeft = Offset(highlightX, padding),
                    size = Size(padding + graphW - highlightX, graphH)
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
            Spacer(modifier = Modifier.width(8.dp))
            HistogramScale.entries.forEach { sc ->
                val isSelected = scale == sc
                Text(
                    text = sc.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color(0xFFB0BEC5) else Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
