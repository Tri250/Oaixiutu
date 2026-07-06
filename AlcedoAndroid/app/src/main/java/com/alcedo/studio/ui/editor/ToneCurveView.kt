package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.alcedo.studio.i18n.stringRes
import kotlin.math.*

data class CurvePoint(val x: Float, val y: Float)

private const val MAX_CONTROL_POINTS = 16
private const val POINT_HIT_RADIUS = 28f
private const val PADDING = 16f

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

    var draggedPointIndex by remember { mutableIntStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }
    val accToneCurveDesc = stringRes { accToneCurve }.format(controlPoints.size)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics {
                contentDescription = accToneCurveDesc
                stateDescription = "${controlPoints.size} control points"
            }
            .pointerInput(controlPoints) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val graphW = size.width - 2 * PADDING
                        val graphH = size.height - 2 * PADDING
                        draggedPointIndex = controlPoints.indexOfFirst { pt ->
                            val px = PADDING + pt.x * graphW
                            val py = PADDING + (1f - pt.y) * graphH
                            sqrt(
                                (startOffset.x - px).pow(2) +
                                    (startOffset.y - py).pow(2)
                            ) < POINT_HIT_RADIUS
                        }
                        isDragging = draggedPointIndex >= 0
                    },
                    onDrag = { change, _ ->
                        if (draggedPointIndex >= 0 && draggedPointIndex < controlPoints.size) {
                            val graphW = size.width - 2 * PADDING
                            val graphH = size.height - 2 * PADDING
                            val newX = ((change.position.x - PADDING) / graphW).coerceIn(0f, 1f)
                            val newY =
                                1f - ((change.position.y - PADDING) / graphH).coerceIn(0f, 1f)

                            val newPoints = controlPoints.toMutableList()
                            if (draggedPointIndex == 0) {
                                newPoints[0] = CurvePoint(0f, newY)
                            } else if (draggedPointIndex == newPoints.size - 1) {
                                newPoints[newPoints.size - 1] = CurvePoint(1f, newY)
                            } else {
                                val clampedX = newX.coerceIn(
                                    newPoints[draggedPointIndex - 1].x + 0.005f,
                                    newPoints[draggedPointIndex + 1].x - 0.005f
                                )
                                newPoints[draggedPointIndex] = CurvePoint(clampedX, newY)
                            }
                            onControlPointsChanged(newPoints)
                        }
                    },
                    onDragEnd = {
                        draggedPointIndex = -1
                        isDragging = false
                    },
                    onDragCancel = {
                        draggedPointIndex = -1
                        isDragging = false
                    }
                )
            }
            .pointerInput(controlPoints) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        val graphW = size.width - 2 * PADDING
                        val graphH = size.height - 2 * PADDING
                        // Check if double-tap is on an existing point
                        val hitIndex = controlPoints.indexOfFirst { pt ->
                            val px = PADDING + pt.x * graphW
                            val py = PADDING + (1f - pt.y) * graphH
                            sqrt(
                                (tapOffset.x - px).pow(2) +
                                    (tapOffset.y - py).pow(2)
                            ) < POINT_HIT_RADIUS
                        }
                        if (hitIndex in 1 until controlPoints.size - 1) {
                            // Reset individual point: remove it
                            val newPoints = controlPoints.toMutableList()
                            newPoints.removeAt(hitIndex)
                            onControlPointsChanged(newPoints)
                        } else {
                            // Reset entire curve to diagonal
                            onControlPointsChanged(
                                listOf(
                                    CurvePoint(0f, 0f),
                                    CurvePoint(0.25f, 0.25f),
                                    CurvePoint(0.5f, 0.5f),
                                    CurvePoint(0.75f, 0.75f),
                                    CurvePoint(1f, 1f)
                                )
                            )
                        }
                    },
                    onTap = { tapOffset ->
                        val graphW = size.width - 2 * PADDING
                        val graphH = size.height - 2 * PADDING
                        val tx = ((tapOffset.x - PADDING) / graphW).coerceIn(0f, 1f)
                        val ty = 1f - ((tapOffset.y - PADDING) / graphH).coerceIn(0f, 1f)

                        val existingIndex = controlPoints.indexOfFirst { pt ->
                            val px = PADDING + pt.x * graphW
                            val py = PADDING + (1f - pt.y) * graphH
                            sqrt(
                                (tapOffset.x - px).pow(2) +
                                    (tapOffset.y - py).pow(2)
                            ) < POINT_HIT_RADIUS
                        }
                        if (existingIndex in 1 until controlPoints.size - 1 && controlPoints.size > 2) {
                            // Remove point (except endpoints)
                            val newPoints = controlPoints.toMutableList()
                            newPoints.removeAt(existingIndex)
                            onControlPointsChanged(newPoints)
                        } else if (existingIndex < 0 && controlPoints.size < MAX_CONTROL_POINTS) {
                            // Add new point
                            val newPoints = controlPoints.toMutableList()
                            newPoints.add(CurvePoint(tx, ty))
                            newPoints.sortBy { it.x }
                            onControlPointsChanged(newPoints)
                        }
                    }
                )
            }
    ) {
        val graphW = size.width - 2 * PADDING
        val graphH = size.height - 2 * PADDING

        // Background
        drawRect(
            color = Color(0xFF1E1E1E),
            topLeft = Offset(PADDING, PADDING),
            size = Size(graphW, graphH)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.1f),
            topLeft = Offset(PADDING, PADDING),
            size = Size(graphW, graphH),
            style = Stroke(width = 1f)
        )

        // Grid
        if (showGrid) {
            // Quarter grid lines
            for (i in 1..3) {
                val x = PADDING + graphW * i / 4f
                val y = PADDING + graphH * i / 4f
                val alpha = if (i == 2) 0.3f else 0.15f
                drawLine(
                    gridColor.copy(alpha = alpha),
                    Offset(x, PADDING), Offset(x, PADDING + graphH),
                    strokeWidth = 0.5f
                )
                drawLine(
                    gridColor.copy(alpha = alpha),
                    Offset(PADDING, y), Offset(PADDING + graphW, y),
                    strokeWidth = 0.5f
                )
            }
        }

        // Diagonal reference line
        drawLine(
            Color.White.copy(alpha = 0.08f),
            Offset(PADDING, PADDING + graphH),
            Offset(PADDING + graphW, PADDING),
            strokeWidth = 1f
        )

        // Histogram
        if (histogramData.isNotEmpty()) {
            val maxCount = histogramData.maxOrNull()?.toFloat() ?: 1f
            val barWidth = graphW / histogramData.size
            for (i in histogramData.indices) {
                val barHeight = (histogramData[i] / maxCount) * graphH * 0.8f
                drawRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = Offset(PADDING + i * barWidth, PADDING + graphH - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }

        // Spline curve using monotone cubic Hermite interpolation
        if (controlPoints.size >= 2) {
            val sortedPoints = controlPoints.sortedBy { it.x }
            val splinePath = computeMonotoneCubicSpline(sortedPoints, graphW, graphH)

            drawPath(
                path = splinePath,
                color = curveColor,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Fill area under curve with subtle gradient
            val fillPath = Path().apply {
                addPath(splinePath)
                lineTo(PADDING + graphW, PADDING + graphH)
                lineTo(PADDING, PADDING + graphH)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    0f to curveColor.copy(alpha = 0.12f),
                    1f to curveColor.copy(alpha = 0.02f)
                )
            )
        }

        // Crosshair on active/dragged point
        if (isDragging && draggedPointIndex in controlPoints.indices) {
            val pt = controlPoints[draggedPointIndex]
            val cx = PADDING + pt.x * graphW
            val cy = PADDING + (1f - pt.y) * graphH

            // Vertical line
            drawLine(
                Color.White.copy(alpha = 0.3f),
                Offset(cx, PADDING),
                Offset(cx, PADDING + graphH),
                strokeWidth = 0.5f
            )
            // Horizontal line
            drawLine(
                Color.White.copy(alpha = 0.3f),
                Offset(PADDING, cy),
                Offset(PADDING + graphW, cy),
                strokeWidth = 0.5f
            )
        }

        // Control points
        controlPoints.forEachIndexed { index, pt ->
            val cx = PADDING + pt.x * graphW
            val cy = PADDING + (1f - pt.y) * graphH
            val isActive = index == draggedPointIndex

            if (isActive) {
                // Active point: larger, highlighted
                drawCircle(
                    color = curveColor.copy(alpha = 0.25f),
                    radius = 14f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = curveColor,
                    radius = 5f,
                    center = Offset(cx, cy)
                )
            } else {
                // Normal point
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = curveColor,
                    radius = 3.5f,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

/**
 * Monotone cubic Hermite interpolation (Fritsch-Carlson method).
 * Prevents overshooting which is critical for tone curves.
 */
private fun computeMonotoneCubicSpline(
    points: List<CurvePoint>,
    graphW: Float,
    graphH: Float
): Path {
    val path = Path()
    if (points.isEmpty()) return path

    val n = points.size
    val screenX = FloatArray(n) { PADDING + points[it].x * graphW }
    val screenY = FloatArray(n) { PADDING + (1f - points[it].y) * graphH }

    path.moveTo(screenX[0], screenY[0])

    if (n == 2) {
        path.lineTo(screenX[1], screenY[1])
        return path
    }

    // Compute secants (deltas)
    val dx = FloatArray(n - 1) { screenX[it + 1] - screenX[it] }
    val dy = FloatArray(n - 1) { screenY[it + 1] - screenY[it] }
    val slopes = FloatArray(n - 1) { if (dx[it] != 0f) dy[it] / dx[it] else 0f }

    // Initialize tangents
    val m = FloatArray(n)
    m[0] = slopes[0]
    for (i in 1 until n - 1) {
        if (slopes[i - 1] * slopes[i] <= 0f) {
            m[i] = 0f
        } else {
            m[i] = (slopes[i - 1] + slopes[i]) / 2f
        }
    }
    m[n - 1] = slopes[n - 2]

    // Fritsch-Carlson: enforce monotonicity
    for (i in 0 until n - 1) {
        if (slopes[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
        } else {
            val alpha = m[i] / slopes[i]
            val beta = m[i + 1] / slopes[i]
            val s = alpha * alpha + beta * beta
            if (s > 9f) {
                val t = 3f / sqrt(s)
                m[i] = t * alpha * slopes[i]
                m[i + 1] = t * beta * slopes[i]
            }
        }
    }

    // Generate curve points using Hermite basis
    val stepsPerSegment = 24
    for (i in 0 until n - 1) {
        val x0 = screenX[i]
        val y0 = screenY[i]
        val x1 = screenX[i + 1]
        val y1 = screenY[i + 1]
        val segDx = dx[i]

        for (j in 1..stepsPerSegment) {
            val t = j.toFloat() / stepsPerSegment
            val h00 = (1 + 2 * t) * (1 - t) * (1 - t)
            val h10 = t * (1 - t) * (1 - t)
            val h01 = t * t * (3 - 2 * t)
            val h11 = t * t * (t - 1)

            val px = h00 * x0 + h10 * segDx * m[i] + h01 * x1 + h11 * segDx * m[i + 1]
            val py = h00 * y0 + h10 * segDx * m[i] + h01 * y1 + h11 * segDx * m[i + 1]
            path.lineTo(px, py)
        }
    }

    return path
}

enum class CurveChannel {
    RGB, RED, GREEN, BLUE
}
