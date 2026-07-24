package com.alcedo.studio.ui.editor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.alcedo.studio.ui.theme.AlcedoEditorColors
import kotlin.math.log10

enum class HistogramChannel(val label: String) {
    RGB("RGB"),
    RED("R"),
    GREEN("G"),
    BLUE("B"),
    LUMINANCE("L"),
    RGB_PARADE("Parade")
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
    showClippingWarning: Boolean = true,
    shadowClipThreshold: Int = 15,
    highlightClipThreshold: Int = 240
) {
    // 剪裁预警动画：闪烁效果
    val infiniteTransition = rememberInfiniteTransition(label = "clippingWarning")
    val clippingAnimation by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "clipBlink"
    )

    // 检测是否有实际剪裁（超过总像素的2%）
    val totalLuminance = histogramData.luminance.sum()
    val safeHighlightThreshold = highlightClipThreshold.coerceIn(0, histogramData.luminance.size - 1)
    val safeShadowThreshold = shadowClipThreshold.coerceIn(0, histogramData.luminance.size - 1)
    val highlightClipped = totalLuminance > 0f && safeHighlightThreshold < histogramData.luminance.size &&
            histogramData.luminance.sliceArray(safeHighlightThreshold..histogramData.luminance.lastIndex).sum() > totalLuminance * 0.02f
    val shadowClipped = totalLuminance > 0f && safeShadowThreshold > 0 &&
            histogramData.luminance.sliceArray(0 until safeShadowThreshold).sum() > totalLuminance * 0.02f

    val scopeBackground = AlcedoEditorColors.scopeBackground
    val onScopeSurface = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showChannels == HistogramChannel.RGB_PARADE) 160.dp else 120.dp)
        ) {
            val w = size.width
            val h = size.height
            val padding = 4f

            // Background
            drawRect(
                color = scopeBackground,
                topLeft = Offset.Zero,
                size = size
            )

            if (showChannels == HistogramChannel.RGB_PARADE) {
                // RGB Parade: three separate histograms side by side
                val gap = 3f
                val paradeWidth = (w - 2 * padding - 2 * gap) / 3f
                val graphH = h - 2 * padding

                drawParadeHistogram(
                    data = histogramData.r,
                    color = AlcedoEditorColors.histogramR,
                    rect = Rect(padding, padding, padding + paradeWidth, padding + graphH),
                    maxCount = histogramData.r.maxOrNull()?.coerceAtLeast(1f) ?: 1f,
                    scale = scale,
                    label = "R"
                )
                drawParadeHistogram(
                    data = histogramData.g,
                    color = AlcedoEditorColors.histogramG,
                    rect = Rect(padding + paradeWidth + gap, padding, padding + 2 * paradeWidth + gap, padding + graphH),
                    maxCount = histogramData.g.maxOrNull()?.coerceAtLeast(1f) ?: 1f,
                    scale = scale,
                    label = "G"
                )
                drawParadeHistogram(
                    data = histogramData.b,
                    color = AlcedoEditorColors.histogramB,
                    rect = Rect(padding + 2 * (paradeWidth + gap), padding, padding + 3 * paradeWidth + 2 * gap, padding + graphH),
                    maxCount = histogramData.b.maxOrNull()?.coerceAtLeast(1f) ?: 1f,
                    scale = scale,
                    label = "B"
                )
            } else {
                // Standard histogram modes
                val graphW = w - 2 * padding
                val graphH = h - 2 * padding

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
                        path.lineTo(x, y)
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
                        drawChannelFill(histogramData.r, AlcedoEditorColors.histogramR, 0.35f)
                        drawChannelFill(histogramData.g, AlcedoEditorColors.histogramG, 0.30f)
                        drawChannelFill(histogramData.b, AlcedoEditorColors.histogramB, 0.30f)
                        drawChannelLine(histogramData.r, AlcedoEditorColors.histogramR, 0.5f)
                        drawChannelLine(histogramData.g, AlcedoEditorColors.histogramG, 0.45f)
                        drawChannelLine(histogramData.b, AlcedoEditorColors.histogramB, 0.45f)
                    }
                    HistogramChannel.RED -> {
                        drawChannelFill(histogramData.r, AlcedoEditorColors.histogramR, 0.5f)
                        drawChannelLine(histogramData.r, AlcedoEditorColors.histogramR, 0.8f)
                    }
                    HistogramChannel.GREEN -> {
                        drawChannelFill(histogramData.g, AlcedoEditorColors.histogramG, 0.5f)
                        drawChannelLine(histogramData.g, AlcedoEditorColors.histogramG, 0.8f)
                    }
                    HistogramChannel.BLUE -> {
                        drawChannelFill(histogramData.b, AlcedoEditorColors.histogramB, 0.5f)
                        drawChannelLine(histogramData.b, AlcedoEditorColors.histogramB, 0.8f)
                    }
                    HistogramChannel.LUMINANCE -> {
                        drawChannelFill(histogramData.luminance, AlcedoEditorColors.histogramLuma, 0.4f)
                        drawChannelLine(histogramData.luminance, AlcedoEditorColors.histogramLuma, 0.7f)
                    }
                    HistogramChannel.RGB_PARADE -> { /* handled above */ }
                }

                // Clipping indicators
                if (showClippingIndicators || showClippingWarning) {
                    val shadowX = padding + (shadowClipThreshold / 255f) * graphW
                    val highlightX = padding + (highlightClipThreshold / 255f) * graphW

                    if (showClippingIndicators && shadowClipped) {
                        drawRect(
                            color = Color(0xFF2979FF).copy(alpha = 0.15f),
                            topLeft = Offset(padding, padding),
                            size = Size(shadowX - padding, graphH)
                        )
                    }
                    if (showClippingIndicators && highlightClipped) {
                        drawRect(
                            color = Color(0xFFFF1744).copy(alpha = 0.15f),
                            topLeft = Offset(highlightX, padding),
                            size = Size(padding + graphW - highlightX, graphH)
                        )
                    }

                    if (showClippingWarning && (highlightClipped || shadowClipped)) {
                        if (highlightClipped) {
                            drawRect(
                                color = Color.Red.copy(alpha = clippingAnimation * 0.5f),
                                topLeft = Offset(highlightX, padding),
                                size = Size(padding + graphW - highlightX, graphH)
                            )
                        }
                        if (shadowClipped) {
                            drawRect(
                                color = Color.Blue.copy(alpha = clippingAnimation * 0.5f),
                                topLeft = Offset(padding, padding),
                                size = Size(shadowX - padding, graphH)
                            )
                        }
                    }
                }

                // Border
                drawRect(
                    color = onScopeSurface.copy(alpha = 0.2f),
                    topLeft = Offset(padding, padding),
                    size = Size(graphW, graphH),
                    style = Stroke(width = 1f)
                )
            }
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
                    HistogramChannel.RED -> AlcedoEditorColors.histogramR
                    HistogramChannel.GREEN -> AlcedoEditorColors.histogramG
                    HistogramChannel.BLUE -> AlcedoEditorColors.histogramB
                    HistogramChannel.LUMINANCE -> Color.White
                    HistogramChannel.RGB_PARADE -> Color.White
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

        // 剪裁预警状态提示
        if (showClippingWarning && (highlightClipped || shadowClipped)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (highlightClipped) Color.Red else Color.Blue,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildString {
                        val parts = mutableListOf<String>()
                        if (highlightClipped) parts += "Highlight clipping"
                        if (shadowClipped) parts += "Shadow clipping"
                        append(parts.joinToString(" · "))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlightClipped) Color.Red else Color.Blue
                )
            }
        }
    }
}

/**
 * Draw a single channel parade histogram within the given rect.
 * Each parade shows a filled area histogram for one color channel.
 */
private fun DrawScope.drawParadeHistogram(
    data: FloatArray,
    color: Color,
    rect: Rect,
    maxCount: Float,
    scale: HistogramScale,
    label: String
) {
    if (data.isEmpty()) return
    val graphW = rect.width
    val graphH = rect.height
    val barWidth = graphW / data.size

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

    // Fill
    val path = Path()
    path.moveTo(rect.left, rect.bottom)
    for (i in data.indices) {
        val barHeight = valueToHeight(data[i])
        val x = rect.left + i * barWidth
        val y = rect.bottom - barHeight
        path.lineTo(x, y)
    }
    path.lineTo(rect.left + data.size * barWidth, rect.bottom)
    path.close()
    drawPath(path, color.copy(alpha = 0.45f))

    // Line
    for (i in 0 until data.size - 1) {
        val h1 = valueToHeight(data[i])
        val h2 = valueToHeight(data[i + 1])
        drawLine(
            color = color.copy(alpha = 0.7f),
            start = Offset(rect.left + i * barWidth, rect.bottom - h1),
            end = Offset(rect.left + (i + 1) * barWidth, rect.bottom - h2),
            strokeWidth = 1f
        )
    }

    // Border
    drawRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 0.5f)
    )
}
