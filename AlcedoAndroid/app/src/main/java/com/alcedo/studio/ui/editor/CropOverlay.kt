package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors

/**
 * Crop overlay composable that draws on top of the image viewport.
 * Renders a dimmed area outside the crop region, draggable corner handles,
 * grid lines (rule of thirds by default), and an aspect-ratio lock indicator.
 *
 * The crop rectangle is in normalized 0..1 coordinates relative to the viewport.
 */
@Composable
fun CropOverlay(
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    modifier: Modifier = Modifier,
    aspectRatio: Float? = null,
    gridType: CropGridType = CropGridType.RULE_OF_THIRDS,
    onCropChange: (left: Float, top: Float, right: Float, bottom: Float) -> Unit = { _, _, _, _ -> },
) {
    var dragCorner by remember { mutableFloatStateOf(-1f) }
    val handleRadius = 12.dp

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width
                        val h = size.height
                        val corners = listOf(
                            Offset(cropLeft * w, cropTop * h),
                            Offset(cropRight * w, cropTop * h),
                            Offset(cropLeft * w, cropBottom * h),
                            Offset(cropRight * w, cropBottom * h),
                        )
                        val threshold = 28.dp.toPx()
                        dragCorner = corners.indices.minByOrNull { i ->
                            val dx = offset.x - corners[i].x
                            val dy = offset.y - corners[i].y
                            kotlin.math.sqrt(dx * dx + dy * dy)
                        }?.takeIf { idx ->
                            val dx = offset.x - corners[idx].x
                            val dy = offset.y - corners[idx].y
                            kotlin.math.sqrt(dx * dx + dy * dy) < threshold
                        }?.toFloat() ?: -1f
                    },
                    onDragEnd = { dragCorner = -1f },
                    onDragCancel = { dragCorner = -1f },
                ) { change, _ ->
                    val w = size.width
                    val h = size.height
                    val nx = (change.position.x / w).coerceIn(0f, 1f)
                    val ny = (change.position.y / h).coerceIn(0f, 1f)
                    val minSize = 0.05f
                    when (dragCorner.toInt()) {
                        0 -> onCropChange(nx.coerceAtMost(cropRight - minSize), ny.coerceAtMost(cropBottom - minSize), cropRight, cropBottom)
                        1 -> onCropChange(cropLeft, ny.coerceAtMost(cropBottom - minSize), nx.coerceAtLeast(cropLeft + minSize), cropBottom)
                        2 -> onCropChange(nx.coerceAtMost(cropRight - minSize), cropTop, cropRight, ny.coerceAtLeast(cropTop + minSize))
                        3 -> onCropChange(cropLeft, cropTop, nx.coerceAtLeast(cropLeft + minSize), ny.coerceAtLeast(cropTop + minSize))
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val left = cropLeft * w
        val top = cropTop * h
        val right = cropRight * w
        val bottom = cropBottom * h
        val cropRect = Rect(left, top, right, bottom)

        // Dimmed area outside crop
        val scrim = AlcedoColors.SurfaceScrim
        // Top
        drawRect(scrim, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, top))
        // Bottom
        drawRect(scrim, topLeft = Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(w, h - bottom))
        // Left
        drawRect(scrim, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(left, bottom - top))
        // Right
        drawRect(scrim, topLeft = Offset(right, top), size = androidx.compose.ui.geometry.Size(w - right, bottom - top))

        // Crop border
        drawRect(
            color = Color.White,
            topLeft = cropRect.topLeft,
            size = cropRect.size,
            style = Stroke(width = 2.dp.toPx()),
        )

        // Grid lines
        val gridColor = Color.White.copy(alpha = 0.35f)
        when (gridType) {
            CropGridType.RULE_OF_THIRDS -> {
                for (i in 1..2) {
                    val xFrac = left + (right - left) * i / 3f
                    val yFrac = top + (bottom - top) * i / 3f
                    drawLine(gridColor, Offset(xFrac, top), Offset(xFrac, bottom), 1.dp.toPx())
                    drawLine(gridColor, Offset(left, yFrac), Offset(right, yFrac), 1.dp.toPx())
                }
            }
            CropGridType.GOLDEN_RATIO -> {
                val phi = 0.618f
                val invPhi = 0.382f
                for (frac in listOf(invPhi, phi)) {
                    val xFrac = left + (right - left) * frac
                    val yFrac = top + (bottom - top) * frac
                    drawLine(gridColor, Offset(xFrac, top), Offset(xFrac, bottom), 1.dp.toPx())
                    drawLine(gridColor, Offset(left, yFrac), Offset(right, yFrac), 1.dp.toPx())
                }
            }
            CropGridType.DIAGONAL -> {
                drawLine(gridColor, Offset(left, top), Offset(right, bottom), 1.dp.toPx())
                drawLine(gridColor, Offset(right, top), Offset(left, bottom), 1.dp.toPx())
            }
            CropGridType.CENTER_CROSS -> {
                val cx = (left + right) / 2f
                val cy = (top + bottom) / 2f
                drawLine(gridColor, Offset(cx, top), Offset(cx, bottom), 1.dp.toPx())
                drawLine(gridColor, Offset(left, cy), Offset(right, cy), 1.dp.toPx())
            }
            CropGridType.NONE -> { /* no grid */ }
        }

        // Corner handles
        val corners = listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(left, bottom),
            Offset(right, bottom),
        )
        corners.forEachIndexed { i, corner ->
            val isActive = dragCorner == i.toFloat()
            val handleColor = if (isActive) AlcedoColors.AccentBlue else Color.White
            val r = handleRadius.toPx()
            // L-shaped corner brackets
            val bracketLen = 18.dp.toPx()
            val thickness = 3.dp.toPx()
            when (i) {
                0 -> { // top-left
                    drawLine(handleColor, Offset(corner.x, corner.y), Offset(corner.x + bracketLen, corner.y), thickness)
                    drawLine(handleColor, Offset(corner.x, corner.y), Offset(corner.x, corner.y + bracketLen), thickness)
                }
                1 -> { // top-right
                    drawLine(handleColor, Offset(corner.x, corner.y), Offset(corner.x - bracketLen, corner.y), thickness)
                    drawLine(handleColor, Offset(corner.x, corner.y), Offset(corner.x, corner.y + bracketLen), thickness)
                }
                2 -> { // bottom-left
                    drawLine(handleColor, Offset(corner.x, corner.y), Offset(corner.x + bracketLen, corner.y), thickness)
                    drawLine(handleColor, Offset(corner.x, corner.y), Offset(corner.x, corner.y - bracketLen), thickness)
                }
                3 -> { // bottom-right
                    drawLine(handleColor, Offset(corner.x, corner.y), Offset(corner.x - bracketLen, corner.y), thickness)
                    drawLine(handleColor, Offset(corner.x, corner.y), Offset(corner.x, corner.y - bracketLen), thickness)
                }
            }
        }

        // Aspect ratio lock indicator
        if (aspectRatio != null) {
            val cx = (left + right) / 2f
            val cy = top - 10.dp.toPx()
            drawCircle(AlcedoColors.AccentBlue, radius = 4.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

enum class CropGridType {
    RULE_OF_THIRDS, GOLDEN_RATIO, DIAGONAL, CENTER_CROSS, NONE
}
