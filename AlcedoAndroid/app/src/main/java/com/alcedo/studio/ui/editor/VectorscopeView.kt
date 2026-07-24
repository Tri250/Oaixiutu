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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcedo.studio.ui.theme.AlcedoEditorColors

/**
 * Professional vectorscope — displays chrominance distribution in Cb-Cr color space.
 *
 * Features:
 * - Circular clip with reference concentric circles and crosshairs
 * - 75% color bar target markers with labels (R, Y, G, C, B, M)
 * - Skin tone line indicator
 * - Cb/Cr colored pixel density plot
 * - Uses Alcedo Design System scope colors
 */
@Composable
fun VectorscopeView(
    vectorscopeData: VectorscopeData,
    modifier: Modifier = Modifier,
    showSkinToneLine: Boolean = true,
    showTargets: Boolean = true,
    backgroundColor: Color = Color.Unspecified
) {
    val effectiveBg = if (backgroundColor == Color.Unspecified) {
        AlcedoEditorColors.scopeBackground
    } else {
        backgroundColor
    }
    val onScopeSurface = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val w = size.width
            val h = size.height
            val displaySize = minOf(w, h)
            val offsetX = (w - displaySize) / 2f
            val offsetY = (h - displaySize) / 2f

            // Background
            drawRect(color = effectiveBg, size = size)

            val cx = offsetX + displaySize / 2f
            val cy = offsetY + displaySize / 2f
            val radius = displaySize / 2f - 12f

            // Clip to circular region
            clipPath(androidx.compose.ui.graphics.Path().apply {
                addOval(Rect(cx - radius - 1, cy - radius - 1, cx + radius + 1, cy + radius + 1))
            }) {
                // Inner background (slightly darker for the scope area)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.2f),
                    radius = radius
                )

                // Concentric reference circles (20%, 40%, 60%, 80%, 100% saturation)
                for (i in 1..5) {
                    val r = radius * i / 5f
                    drawCircle(
                        color = onScopeSurface.copy(alpha = if (i == 5) 0.12f else 0.06f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = if (i == 5) 0.8f else 0.5f)
                    )
                }

                // Crosshair at center (Cb=0, Cr=0 = neutral)
                drawLine(
                    onScopeSurface.copy(alpha = 0.10f),
                    start = Offset(cx - radius, cy),
                    end = Offset(cx + radius, cy),
                    strokeWidth = 0.5f
                )
                drawLine(
                    onScopeSurface.copy(alpha = 0.10f),
                    start = Offset(cx, cy - radius),
                    end = Offset(cx, cy + radius),
                    strokeWidth = 0.5f
                )

                // Diagonal crosshairs for better orientation
                val diagLen = radius * 0.707f  // cos(45°)
                drawLine(
                    onScopeSurface.copy(alpha = 0.05f),
                    start = Offset(cx - diagLen, cy - diagLen),
                    end = Offset(cx + diagLen, cy + diagLen),
                    strokeWidth = 0.5f
                )
                drawLine(
                    onScopeSurface.copy(alpha = 0.05f),
                    start = Offset(cx - diagLen, cy + diagLen),
                    end = Offset(cx + diagLen, cy - diagLen),
                    strokeWidth = 0.5f
                )

                // Skin tone line
                if (showSkinToneLine) {
                    val skinLine = ScopeAnalyzer.SKIN_TONE_LINE
                    val x1 = cx + (skinLine.first.first - 0.5f) * radius * 2f
                    val y1 = cy + (skinLine.first.second - 0.5f) * radius * 2f
                    val x2 = cx + (skinLine.second.first - 0.5f) * radius * 2f
                    val y2 = cy + (skinLine.second.second - 0.5f) * radius * 2f
                    drawLine(
                        Color(0xFFE8A87C).copy(alpha = 0.7f),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 1.5f
                    )
                    // Skin tone label
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "Skin",
                        topLeft = Offset(x2 + 3f, y2 - 5f),
                        style = TextStyle(
                            color = Color(0xFFE8A87C).copy(alpha = 0.6f),
                            fontSize = 7.sp
                        )
                    )
                }

                // Plot vectorscope data
                val dataSize = vectorscopeData.size
                if (dataSize > 0 && vectorscopeData.bins.isNotEmpty()) {
                    val maxBin = vectorscopeData.bins.maxOrNull() ?: 1
                    val scale = displaySize / dataSize.toFloat()
                    val pixelSize = (scale * 0.8f).coerceAtLeast(1f)

                    for (by in 0 until dataSize) {
                        for (bx in 0 until dataSize) {
                            val count = vectorscopeData.bins[by * dataSize + bx]
                            if (count > 0) {
                                val alpha = (count.toFloat() / maxBin.toFloat()).coerceIn(0.05f, 1f)
                                val px = offsetX + bx * scale
                                val py = offsetY + by * scale

                                // Color the point based on its Cb/Cr position
                                val cb = bx.toFloat() / dataSize
                                val cr = by.toFloat() / dataSize
                                val pointColor = cbCrToColor(cb, cr, alpha)
                                drawRect(
                                    color = pointColor,
                                    topLeft = Offset(px, py),
                                    size = Size(pixelSize, pixelSize)
                                )
                            }
                        }
                    }
                }

                // 75% color bar targets with labels
                if (showTargets) {
                    val targetLabelStyle = TextStyle(
                        color = onScopeSurface.copy(alpha = 0.55f),
                        fontSize = 8.sp
                    )
                    ScopeAnalyzer.VECTORSCOPE_TARGETS.forEach { (label, cbCr) ->
                        val tx = cx + (cbCr.first - 0.5f) * radius * 2f
                        val ty = cy + (cbCr.second - 0.5f) * radius * 2f

                        // Target line from center
                        drawLine(
                            onScopeSurface.copy(alpha = 0.12f),
                            start = Offset(cx, cy),
                            end = Offset(tx, ty),
                            strokeWidth = 0.5f
                        )

                        // Target circle marker (small box with cross)
                        drawCircle(
                            color = onScopeSurface.copy(alpha = 0.5f),
                            radius = 4f,
                            center = Offset(tx, ty),
                            style = Stroke(width = 1f)
                        )
                        // Cross inside target
                        drawLine(
                            onScopeSurface.copy(alpha = 0.4f),
                            start = Offset(tx - 3f, ty),
                            end = Offset(tx + 3f, ty),
                            strokeWidth = 0.5f
                        )
                        drawLine(
                            onScopeSurface.copy(alpha = 0.4f),
                            start = Offset(tx, ty - 3f),
                            end = Offset(tx, ty + 3f),
                            strokeWidth = 0.5f
                        )

                        // Target label
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(tx + 5f, ty - 5f),
                            style = targetLabelStyle
                        )
                    }
                }

                // Center dot (neutral)
                drawCircle(
                    color = onScopeSurface.copy(alpha = 0.3f),
                    radius = 2f,
                    center = Offset(cx, cy)
                )
            }

            // Outer circle border
            drawCircle(
                color = onScopeSurface.copy(alpha = 0.25f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 1f)
            )

            // Axis labels
            val axisStyle = TextStyle(
                color = onScopeSurface.copy(alpha = 0.3f),
                fontSize = 7.sp
            )
            drawText(textMeasurer, "Cb+", Offset(cx + radius - 14f, cy + 2f), style = axisStyle)
            drawText(textMeasurer, "Cb−", Offset(cx - radius + 2f, cy + 2f), style = axisStyle)
            drawText(textMeasurer, "Cr+", Offset(cx + 3f, cy - radius + 2f), style = axisStyle)
            drawText(textMeasurer, "Cr−", Offset(cx + 3f, cy + radius - 10f), style = axisStyle)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Vectorscope",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (showTargets) {
                Text(
                    text = "• 75% Bars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (showSkinToneLine) {
                Text(
                    text = "• Skin Tone",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE8A87C).copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/** Map Cb/Cr position to a tinted display color with the given alpha. */
private fun cbCrToColor(cb: Float, cr: Float, alpha: Float): Color {
    val cbN = cb - 0.5f  // -0.5..0.5
    val crN = cr - 0.5f

    // Approximate reverse YCbCr → RGB for visual tinting
    val r = (0.5f + crN * 1.4f).coerceIn(0f, 1f)
    val g = (0.5f - cbN * 0.344f - crN * 0.714f).coerceIn(0f, 1f)
    val b = (0.5f + cbN * 1.772f).coerceIn(0f, 1f)

    return Color(red = r, green = g, blue = b, alpha = alpha)
}
