package com.alcedo.studio.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing
import kotlin.math.*

data class CurveControlPoint(val x: Float, val y: Float)

enum class CurveV2Channel(val label: String) {
    RGB("RGB"),
    R("R"),
    G("G"),
    B("B")
}

private const val MAX_V2_CONTROL_POINTS = 16
private const val POINT_HIT_RADIUS = 28f
private const val CV2_PADDING = 16f

@Composable
fun CurvePanelV2(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier,
    histogramData: List<Int> = emptyList()
) {
    var selectedChannel by remember { mutableStateOf(CurveV2Channel.RGB) }
    var draggedPointIndex by remember { mutableIntStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }
    val alcedoColors = LocalAlcedoColors.current

    val controlPoints = remember(params.toneCurveX, params.toneCurveY) {
        val count = params.toneCurvePoints
        (0 until count).map { i ->
            CurveControlPoint(params.toneCurveX[i], params.toneCurveY[i])
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
    ) {
        // Channel tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
        ) {
            CurveV2Channel.entries.forEach { ch ->
                val isSelected = selectedChannel == ch
                val color = when (ch) {
                    CurveV2Channel.RGB -> alcedoColors.accent
                    CurveV2Channel.R -> alcedoColors.danger
                    CurveV2Channel.G -> Color(0xFF66BB6A)
                    CurveV2Channel.B -> Color(0xFF42A5F5)
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedChannel = ch },
                    label = {
                        Text(
                            ch.label,
                            color = if (isSelected) color else alcedoColors.textMuted
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color.copy(alpha = 0.15f),
                        selectedLabelColor = color
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Interactive curve graph with histogram background
        CurveV2Graph(
            controlPoints = controlPoints,
            onControlPointsChanged = { newPoints ->
                val newX = FloatArray(16) { if (it < newPoints.size) newPoints[it].x else 0f }
                val newY = FloatArray(16) { if (it < newPoints.size) newPoints[it].y else 0f }
                onParamsChanged(
                    params.copy(
                        toneCurveX = newX,
                        toneCurveY = newY,
                        toneCurvePoints = newPoints.size
                    )
                )
            },
            channel = selectedChannel,
            histogramData = histogramData,
            draggedPointIndex = draggedPointIndex,
            isDragging = isDragging,
            onDraggedPointIndexChanged = { draggedPointIndex = it },
            onIsDraggingChanged = { isDragging = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AlcedoSpacing.md)
        )

        // Reset button
        OutlinedButton(
            onClick = {
                onParamsChanged(
                    params.copy(
                        toneCurveX = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                        toneCurveY = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                        toneCurvePoints = 5
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(AlcedoIconSize.sm)
            )
            Spacer(modifier = Modifier.width(AlcedoSpacing.sm))
            Text(stringRes { curveResetButton })
        }

        // Curve info
        SectionHeader(title = "Curve Info") {
            Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)) {
                Text(
                    "Points: ${controlPoints.size} / $MAX_V2_CONTROL_POINTS",
                    style = AlcedoFontRoles.uiCaption,
                    color = alcedoColors.textMuted
                )
                Text(
                    "Channel: ${selectedChannel.label}",
                    style = AlcedoFontRoles.uiCaption,
                    color = alcedoColors.textMuted
                )
                Text(
                    "Spline: Hermite monotone-preserving",
                    style = AlcedoFontRoles.uiCaption,
                    color = alcedoColors.textMuted
                )
            }
        }
    }
}

@Composable
private fun CurveV2Graph(
    controlPoints: List<CurveControlPoint>,
    onControlPointsChanged: (List<CurveControlPoint>) -> Unit,
    channel: CurveV2Channel,
    histogramData: List<Int>,
    draggedPointIndex: Int,
    isDragging: Boolean,
    onDraggedPointIndexChanged: (Int) -> Unit,
    onIsDraggingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val curveColor = when (channel) {
        CurveV2Channel.RGB -> alcedoColors.text
        CurveV2Channel.R -> alcedoColors.danger
        CurveV2Channel.G -> Color(0xFF66BB6A)
        CurveV2Channel.B -> Color(0xFF42A5F5)
    }

    val gridColor = alcedoColors.outlineVariant
    val surfaceContainerColor = alcedoColors.surfaceContainer
    val onSurfaceColor = alcedoColors.text

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(controlPoints) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val graphW = size.width - 2 * CV2_PADDING
                        val graphH = size.height - 2 * CV2_PADDING
                        onDraggedPointIndexChanged(
                            controlPoints.indexOfFirst { pt ->
                                val px = CV2_PADDING + pt.x * graphW
                                val py = CV2_PADDING + (1f - pt.y) * graphH
                                sqrt(
                                    (startOffset.x - px).pow(2) +
                                        (startOffset.y - py).pow(2)
                                ) < POINT_HIT_RADIUS
                            }
                        )
                        onIsDraggingChanged(draggedPointIndex >= 0)
                    },
                    onDrag = { change, _ ->
                        val idx = draggedPointIndex
                        if (idx >= 0 && idx < controlPoints.size) {
                            val graphW = size.width - 2 * CV2_PADDING
                            val graphH = size.height - 2 * CV2_PADDING
                            val newX = ((change.position.x - CV2_PADDING) / graphW).coerceIn(0f, 1f)
                            val newY = 1f - ((change.position.y - CV2_PADDING) / graphH).coerceIn(0f, 1f)

                            val newPoints = controlPoints.toMutableList()
                            if (idx == 0) {
                                newPoints[0] = CurveControlPoint(0f, newY)
                            } else if (idx == newPoints.size - 1) {
                                newPoints[newPoints.size - 1] = CurveControlPoint(1f, newY)
                            } else {
                                val clampedX = newX.coerceIn(
                                    newPoints[idx - 1].x + 0.005f,
                                    newPoints[idx + 1].x - 0.005f
                                )
                                newPoints[idx] = CurveControlPoint(clampedX, newY)
                            }
                            onControlPointsChanged(newPoints)
                        }
                    },
                    onDragEnd = {
                        onDraggedPointIndexChanged(-1)
                        onIsDraggingChanged(false)
                    },
                    onDragCancel = {
                        onDraggedPointIndexChanged(-1)
                        onIsDraggingChanged(false)
                    }
                )
            }
            .pointerInput(controlPoints) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val graphW = size.width - 2 * CV2_PADDING
                        val graphH = size.height - 2 * CV2_PADDING
                        val tx = ((tapOffset.x - CV2_PADDING) / graphW).coerceIn(0f, 1f)
                        val ty = 1f - ((tapOffset.y - CV2_PADDING) / graphH).coerceIn(0f, 1f)

                        val existingIndex = controlPoints.indexOfFirst { pt ->
                            val px = CV2_PADDING + pt.x * graphW
                            val py = CV2_PADDING + (1f - pt.y) * graphH
                            sqrt(
                                (tapOffset.x - px).pow(2) +
                                    (tapOffset.y - py).pow(2)
                            ) < POINT_HIT_RADIUS
                        }
                        if (existingIndex in 1 until controlPoints.size - 1 && controlPoints.size > 2) {
                            // Remove point
                            val newPoints = controlPoints.toMutableList()
                            newPoints.removeAt(existingIndex)
                            onControlPointsChanged(newPoints)
                        } else if (existingIndex < 0 && controlPoints.size < MAX_V2_CONTROL_POINTS) {
                            // Add new point
                            val newPoints = controlPoints.toMutableList()
                            newPoints.add(CurveControlPoint(tx, ty))
                            newPoints.sortBy { it.x }
                            onControlPointsChanged(newPoints)
                        }
                    }
                )
            }
    ) {
        val graphW = size.width - 2 * CV2_PADDING
        val graphH = size.height - 2 * CV2_PADDING

        // Background
        drawRect(
            color = surfaceContainerColor,
            topLeft = Offset(CV2_PADDING, CV2_PADDING),
            size = Size(graphW, graphH)
        )
        drawRect(
            color = onSurfaceColor.copy(alpha = 0.1f),
            topLeft = Offset(CV2_PADDING, CV2_PADDING),
            size = Size(graphW, graphH),
            style = Stroke(width = 1f)
        )

        // Grid
        for (i in 1..3) {
            val x = CV2_PADDING + graphW * i / 4f
            val y = CV2_PADDING + graphH * i / 4f
            val alpha = if (i == 2) 0.3f else 0.15f
            drawLine(
                gridColor.copy(alpha = alpha),
                Offset(x, CV2_PADDING), Offset(x, CV2_PADDING + graphH),
                strokeWidth = 0.5f
            )
            drawLine(
                gridColor.copy(alpha = alpha),
                Offset(CV2_PADDING, y), Offset(CV2_PADDING + graphW, y),
                strokeWidth = 0.5f
            )
        }

        // Diagonal reference line
        drawLine(
            onSurfaceColor.copy(alpha = 0.08f),
            Offset(CV2_PADDING, CV2_PADDING + graphH),
            Offset(CV2_PADDING + graphW, CV2_PADDING),
            strokeWidth = 1f
        )

        // Simplified histogram background
        if (histogramData.isNotEmpty()) {
            val maxCount = histogramData.maxOrNull()?.toFloat() ?: 1f
            val barWidth = graphW / histogramData.size
            for (i in histogramData.indices) {
                val barHeight = (histogramData[i] / maxCount) * graphH * 0.7f
                drawRect(
                    color = onSurfaceColor.copy(alpha = 0.05f),
                    topLeft = Offset(
                        CV2_PADDING + i * barWidth,
                        CV2_PADDING + graphH - barHeight
                    ),
                    size = Size(barWidth, barHeight)
                )
            }
        }

        // Hermite monotone-preserving spline curve
        if (controlPoints.size >= 2) {
            val sortedPoints = controlPoints.sortedBy { it.x }
            val splinePath = computeV2MonotoneCubicSpline(sortedPoints, graphW, graphH)

            drawPath(
                path = splinePath,
                color = curveColor,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Fill area under curve
            val fillPath = Path().apply {
                addPath(splinePath)
                lineTo(CV2_PADDING + graphW, CV2_PADDING + graphH)
                lineTo(CV2_PADDING, CV2_PADDING + graphH)
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
            val cx = CV2_PADDING + pt.x * graphW
            val cy = CV2_PADDING + (1f - pt.y) * graphH

            drawLine(
                onSurfaceColor.copy(alpha = 0.3f),
                Offset(cx, CV2_PADDING),
                Offset(cx, CV2_PADDING + graphH),
                strokeWidth = 0.5f
            )
            drawLine(
                onSurfaceColor.copy(alpha = 0.3f),
                Offset(CV2_PADDING, cy),
                Offset(CV2_PADDING + graphW, cy),
                strokeWidth = 0.5f
            )

            // Value readout
            val inVal = (pt.x * 255).toInt()
            val outVal = (pt.y * 255).toInt()
            drawRect(
                color = Color(0xFF333333),
                topLeft = Offset(cx + 8f, cy - 18f),
                size = Size(60f, 16f)
            )
        }

        // Control points
        controlPoints.forEachIndexed { index, pt ->
            val cx = CV2_PADDING + pt.x * graphW
            val cy = CV2_PADDING + (1f - pt.y) * graphH
            val isActive = index == draggedPointIndex

            if (isActive) {
                drawCircle(
                    color = curveColor.copy(alpha = 0.25f),
                    radius = 14f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = onSurfaceColor,
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
                drawCircle(
                    color = onSurfaceColor,
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
private fun computeV2MonotoneCubicSpline(
    points: List<CurveControlPoint>,
    graphW: Float,
    graphH: Float
): Path {
    val path = Path()
    if (points.isEmpty()) return path

    val n = points.size
    val screenX = FloatArray(n) { CV2_PADDING + points[it].x * graphW }
    val screenY = FloatArray(n) { CV2_PADDING + (1f - points[it].y) * graphH }

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
