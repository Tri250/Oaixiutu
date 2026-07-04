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

@Composable
fun VectorscopeView(
    vectorscopeData: VectorscopeData,
    modifier: Modifier = Modifier,
    showSkinToneLine: Boolean = true,
    showTargets: Boolean = true,
    backgroundColor: Color = Color(0xFF1A1A1A)
) {
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
            drawRect(color = backgroundColor, size = size)

            val cx = offsetX + displaySize / 2f
            val cy = offsetY + displaySize / 2f
            val radius = displaySize / 2f - 8f

            // Draw concentric reference circles
            for (i in 1..3) {
                val r = radius * i / 3f
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.5f)
                )
            }

            // Crosshair at center
            drawLine(
                Color.White.copy(alpha = 0.1f),
                start = Offset(cx - radius, cy),
                end = Offset(cx + radius, cy),
                strokeWidth = 0.5f
            )
            drawLine(
                Color.White.copy(alpha = 0.1f),
                start = Offset(cx, cy - radius),
                end = Offset(cx, cy + radius),
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
                    Color(0xFFE8A87C).copy(alpha = 0.6f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1.5f
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

            // Color targets
            if (showTargets) {
                ScopeAnalyzer.VECTORSCOPE_TARGETS.forEach { (label, cbCr) ->
                    val tx = cx + (cbCr.first - 0.5f) * radius * 2f
                    val ty = cy + (cbCr.second - 0.5f) * radius * 2f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = 3f,
                        center = Offset(tx, ty),
                        style = Stroke(width = 1f)
                    )
                    // Draw target lines from center
                    drawLine(
                        Color.White.copy(alpha = 0.15f),
                        start = Offset(cx, cy),
                        end = Offset(tx, ty),
                        strokeWidth = 0.5f
                    )
                }
            }

            // Outer circle border
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Vectorscope",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** Map Cb/Cr position to a tinted display color with the given alpha. */
private fun cbCrToColor(cb: Float, cr: Float, alpha: Float): Color {
    // Convert normalized Cb,Cr back to approximate RGB for display tint
    val cbN = cb - 0.5f  // -0.5..0.5
    val crN = cr - 0.5f

    // Approximate reverse YCbCr → RGB for visual tinting
    val r = (0.5f + crN * 1.4f).coerceIn(0f, 1f)
    val g = (0.5f - cbN * 0.344f - crN * 0.714f).coerceIn(0f, 1f)
    val b = (0.5f + cbN * 1.772f).coerceIn(0f, 1f)

    return Color(red = r, green = g, blue = b, alpha = alpha)
}
