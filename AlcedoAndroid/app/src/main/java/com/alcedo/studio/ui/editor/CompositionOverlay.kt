package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors

/**
 * Composition guide overlays drawn on top of the image viewport.
 * Supports Rule of Thirds, Golden Ratio, Diagonal, and Center cross guides.
 * The guides are rendered as semi-transparent lines.
 */
@Composable
fun CompositionOverlay(
    guideType: CompositionGuide,
    modifier: Modifier = Modifier,
    color: Color = AlcedoColors.AccentBlue.copy(alpha = 0.4f),
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (guideType) {
            CompositionGuide.RULE_OF_THIRDS -> {
                for (i in 1..2) {
                    val x = w * i / 3f
                    val y = h * i / 3f
                    drawLine(color, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                    drawLine(color, Offset(0f, y), Offset(w, y), 1.dp.toPx())
                }
            }
            CompositionGuide.GOLDEN_RATIO -> {
                val phi = 0.618f
                val invPhi = 1f - phi
                for (frac in listOf(invPhi, phi)) {
                    val x = w * frac
                    val y = h * frac
                    drawLine(color, Offset(x, 0f), Offset(x, h), 1.dp.toPx())
                    drawLine(color, Offset(0f, y), Offset(w, y), 1.dp.toPx())
                }
                // Golden spiral approximation: quarter circles at each intersection
                val radius = minOf(w, h) * invPhi * 0.5f
                val centers = listOf(
                    Offset(w * invPhi, h * invPhi),
                    Offset(w * phi, h * invPhi),
                    Offset(w * phi, h * phi),
                    Offset(w * invPhi, h * phi),
                )
                val startAngles = listOf(180f, 270f, 0f, 90f)
                centers.forEachIndexed { i, center ->
                    drawArc(
                        color = color,
                        startAngle = startAngles[i],
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            CompositionGuide.DIAGONAL -> {
                drawLine(color, Offset(0f, 0f), Offset(w, h), 1.dp.toPx())
                drawLine(color, Offset(w, 0f), Offset(0f, h), 1.dp.toPx())
                // Also draw the midlines for reference
                drawLine(
                    color.copy(alpha = color.alpha * 0.5f),
                    Offset(w / 2f, 0f), Offset(w / 2f, h), 1.dp.toPx(),
                )
                drawLine(
                    color.copy(alpha = color.alpha * 0.5f),
                    Offset(0f, h / 2f), Offset(w, h / 2f), 1.dp.toPx(),
                )
            }
            CompositionGuide.CENTER_CROSS -> {
                val cx = w / 2f
                val cy = h / 2f
                drawLine(color, Offset(cx, 0f), Offset(cx, h), 1.dp.toPx())
                drawLine(color, Offset(0f, cy), Offset(w, cy), 1.dp.toPx())
                // Corner-to-center lines
                drawLine(
                    color.copy(alpha = color.alpha * 0.5f),
                    Offset(0f, 0f), Offset(cx, cy), 1.dp.toPx(),
                )
                drawLine(
                    color.copy(alpha = color.alpha * 0.5f),
                    Offset(w, 0f), Offset(cx, cy), 1.dp.toPx(),
                )
                drawLine(
                    color.copy(alpha = color.alpha * 0.5f),
                    Offset(0f, h), Offset(cx, cy), 1.dp.toPx(),
                )
                drawLine(
                    color.copy(alpha = color.alpha * 0.5f),
                    Offset(w, h), Offset(cx, cy), 1.dp.toPx(),
                )
            }
            CompositionGuide.NONE -> { /* no overlay */ }
        }
    }
}

enum class CompositionGuide {
    RULE_OF_THIRDS, GOLDEN_RATIO, DIAGONAL, CENTER_CROSS, NONE
}
