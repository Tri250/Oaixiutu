package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.*

data class CurvePoint(val x: Float, val y: Float)

@Composable
fun ToneCurveView(
    controlPoints: List<CurvePoint>,
    onControlPointsChanged: (List<CurvePoint>) -> Unit,
    modifier: Modifier = Modifier,
    histogramData: List<Int> = emptyList(),
    channel: CurveChannel = CurveChannel.RGB,
    onChannelChanged: (CurveChannel) -> Unit = {},
    showGrid: Boolean = true
) {
    val gridColor = Color.Gray.copy(alpha = 0.3f)
    val curveColor = when (channel) {
        CurveChannel.RGB -> Color.White
        CurveChannel.RED -> Color.Red
        CurveChannel.GREEN -> Color.Green
        CurveChannel.BLUE -> Color(0xFF4488FF)
    }

    var draggedPointIndex by remember { mutableStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(controlPoints.size) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val padding = 20f
                        val graphW = size.width - 2 * padding
                        val graphH = size.height - 2 * padding
                        draggedPointIndex = controlPoints.indexOfFirst { pt ->
                            val px = padding + pt.x * graphW
                            val py = padding + (1f - pt.y) * graphH
                            sqrt((startOffset.x - px).pow(2) + (startOffset.y - py).pow(2)) < 30f
                        }
                    },
                    onDrag = { change, _ ->
                        if (draggedPointIndex >= 0 && draggedPointIndex < controlPoints.size) {
                            val padding = 20f
                            val graphW = size.width - 2 * padding
                            val graphH = size.height - 2 * padding
                            val newX = ((change.position.x - padding) / graphW).coerceIn(0f, 1f)
                            val newY = 1f - ((change.position.y - padding) / graphH).coerceIn(0f, 1f)

                            val newPoints = controlPoints.toMutableList()
                            if (draggedPointIndex == 0) {
                                newPoints[0] = CurvePoint(0f, newY)
                            } else if (draggedPointIndex == newPoints.size - 1) {
                                newPoints[newPoints.size - 1] = CurvePoint(1f, newY)
                            } else {
                                val clampedX = newX.coerceIn(
                                    newPoints[draggedPointIndex - 1].x + 0.01f,
                                    newPoints[draggedPointIndex + 1].x - 0.01f
                                )
                                newPoints[draggedPointIndex] = CurvePoint(clampedX, newY)
                            }
                            onControlPointsChanged(newPoints)
                        }
                    },
                    onDragEnd = { draggedPointIndex = -1 }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onControlPointsChanged(
                            listOf(
                                CurvePoint(0f, 0f),
                                CurvePoint(0.25f, 0.25f),
                                CurvePoint(0.5f, 0.5f),
                                CurvePoint(0.75f, 0.75f),
                                CurvePoint(1f, 1f)
                            )
                        )
                    },
                    onTap = { tapOffset ->
                        val padding = 20f
                        val graphW = size.width - 2 * padding
                        val graphH = size.height - 2 * padding
                        val tx = ((tapOffset.x - padding) / graphW).coerceIn(0f, 1f)
                        val ty = 1f - ((tapOffset.y - padding) / graphH).coerceIn(0f, 1f)

                        val existingIndex = controlPoints.indexOfFirst { pt ->
                            val px = padding + pt.x * graphW
                            val py = padding + (1f - pt.y) * graphH
                            sqrt((tapOffset.x - px).pow(2) + (tapOffset.y - py).pow(2)) < 25f
                        }
                        if (existingIndex >= 0 && controlPoints.size > 2) {
                            val newPoints = controlPoints.toMutableList()
                            newPoints.removeAt(existingIndex)
                            onControlPointsChanged(newPoints)
                        } else if (existingIndex < 0 && controlPoints.size < 10) {
                            val newPoints = controlPoints.toMutableList()
                            newPoints.add(CurvePoint(tx, ty))
                            newPoints.sortBy { it.x }
                            onControlPointsChanged(newPoints)
                        }
                    }
                )
            }
    ) {
        val padding = 20f
        val graphW = size.width - 2 * padding
        val graphH = size.height - 2 * padding
        val graphRect = Rect(
            padding, padding,
            size.width - padding, size.height - padding
        )

        // Background
        drawRect(
            color = Color(0xFF2A2A2A),
            topLeft = Offset(padding, padding),
            size = Size(graphW, graphH)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.1f),
            topLeft = Offset(padding, padding),
            size = Size(graphW, graphH),
            style = Stroke(width = 1f)
        )

        // Grid
        if (showGrid) {
            // Rule of thirds
            for (i in 1..2) {
                val x = padding + graphW * i / 3f
                val y = padding + graphH * i / 3f
                drawLine(gridColor, Offset(x, padding), Offset(x, padding + graphH), strokeWidth = 0.5f)
                drawLine(gridColor, Offset(padding, y), Offset(padding + graphW, y), strokeWidth = 0.5f)
            }
            // Quarter grid
            for (i in 1..3) {
                if (i == 2) continue
                val x = padding + graphW * i / 4f
                val y = padding + graphH * i / 4f
                drawLine(
                    gridColor.copy(alpha = 0.15f),
                    Offset(x, padding), Offset(x, padding + graphH),
                    strokeWidth = 0.5f
                )
                drawLine(
                    gridColor.copy(alpha = 0.15f),
                    Offset(padding, y), Offset(padding + graphW, y),
                    strokeWidth = 0.5f
                )
            }
        }

        // Histogram
        if (histogramData.isNotEmpty()) {
            val maxCount = histogramData.maxOrNull()?.toFloat() ?: 1f
            val barWidth = graphW / histogramData.size
            for (i in histogramData.indices) {
                val barHeight = (histogramData[i] / maxCount) * graphH * 0.8f
                drawRect(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(padding + i * barWidth, padding + graphH - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }

        // Spline curve
        if (controlPoints.size >= 2) {
            val path = Path()
            val sortedPoints = controlPoints.sortedBy { it.x }
            val screenPoints = sortedPoints.map { pt ->
                Offset(padding + pt.x * graphW, padding + (1f - pt.y) * graphH)
            }

            path.moveTo(screenPoints[0].x, screenPoints[0].y)

            if (sortedPoints.size == 2) {
                path.lineTo(screenPoints[1].x, screenPoints[1].y)
            } else {
                // Catmull-Rom spline
                for (i in 0 until screenPoints.size - 1) {
                    val p0 = screenPoints[(i - 1).coerceAtLeast(0)]
                    val p1 = screenPoints[i]
                    val p2 = screenPoints[i + 1]
                    val p3 = screenPoints[(i + 2).coerceAtMost(screenPoints.size - 1)]

                    val steps = 20
                    for (j in 1..steps) {
                        val t = j.toFloat() / steps
                        val t2 = t * t
                        val t3 = t2 * t
                        val x = 0.5f * (
                            (2 * p1.x) +
                            (-p0.x + p2.x) * t +
                            (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                            (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3
                        )
                        val y = 0.5f * (
                            (2 * p1.y) +
                            (-p0.y + p2.y) * t +
                            (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                            (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3
                        )
                        path.lineTo(x, y)
                    }
                }
            }

            drawPath(
                path = path,
                color = curveColor,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Control points
        controlPoints.forEach { pt ->
            val cx = padding + pt.x * graphW
            val cy = padding + (1f - pt.y) * graphH
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = curveColor,
                radius = 4f,
                center = Offset(cx, cy)
            )
        }
    }
}

enum class CurveChannel {
    RGB, RED, GREEN, BLUE
}