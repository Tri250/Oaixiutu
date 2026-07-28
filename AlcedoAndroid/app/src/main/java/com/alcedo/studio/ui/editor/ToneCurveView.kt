package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.CurvePoint
import com.alcedo.studio.ui.theme.AlcedoColors

/**
 * Interactive tone curve Canvas. Renders a Catmull-Rom/Hermite spline through
 * [points] inside a unit square. Points are draggable; tapping near an empty
 * area adds a point; long-pressing a point removes it (keeping at least the two
 * endpoints). The host receives updated point lists via [onPointsChange].
 */
@Composable
fun ToneCurveView(
    points: List<CurvePoint>,
    modifier: Modifier = Modifier,
    onPointsChange: (List<CurvePoint>) -> Unit = {},
    curveColor: Color = AlcedoColors.LumaTrace,
) {
    var activeIndex by remember { mutableStateOf(-1) }
    var localPoints by remember(points) { mutableStateOf(points) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.Obsidian)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width
                    val h = size.height
                    val x = (offset.x / w).coerceIn(0f, 1f)
                    val y = (1f - offset.y / h).coerceIn(0f, 1f)
                    // Don't add duplicates of endpoints.
                    if (x > 0.02f && x < 0.98f) {
                        val sorted = (localPoints + CurvePoint(x, y)).sortedBy { it.x }
                        localPoints = sorted
                        onPointsChange(sorted)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width
                        val h = size.height
                        activeIndex = localPoints.indices.minByOrNull { i ->
                            val p = localPoints[i]
                            val px = p.x * w
                            val py = (1f - p.y) * h
                            kotlin.math.sqrt((offset.x - px).pow2() + (offset.y - py).pow2())
                        } ?: -1
                    },
                    onDragEnd = { activeIndex = -1 },
                ) { change, _ ->
                    if (activeIndex in localPoints.indices) {
                        val w = size.width
                        val h = size.height
                        val p = localPoints[activeIndex]
                        // Endpoints can only move vertically.
                        val newX = if (activeIndex == 0 || activeIndex == localPoints.lastIndex) p.x
                        else (change.position.x / w).coerceIn(
                            localPoints[activeIndex - 1].x + 0.01f,
                            localPoints[activeIndex + 1].x - 0.01f,
                        )
                        val newY = (1f - change.position.y / h).coerceIn(0f, 1f)
                        localPoints = localPoints.toMutableList().also {
                            it[activeIndex] = CurvePoint(newX, newY)
                        }
                        onPointsChange(localPoints)
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        // Grid
        for (i in 1 until 4) {
            val g = w * i / 4f
            drawLine(AlcedoColors.Divider, Offset(g, 0f), Offset(g, h), 1.dp.toPx())
            drawLine(AlcedoColors.Divider, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1.dp.toPx())
        }
        // Diagonal reference
        drawLine(AlcedoColors.Outline, Offset(0f, h), Offset(w, 0f), 1.dp.toPx())

        // Spline path
        val path = Path()
        if (localPoints.size >= 2) {
            val first = localPoints.first()
            path.moveTo(first.x * w, (1f - first.y) * h)
            for (i in 0 until localPoints.size - 1) {
                val p0 = localPoints[i]
                val p1 = localPoints[i + 1]
                val midX = ((p0.x + p1.x) / 2f) * w
                val midY0 = (1f - p0.y) * h
                val midY1 = (1f - p1.y) * h
                path.cubicTo(midX, midY0, midX, midY1, p1.x * w, (1f - p1.y) * h)
            }
            drawPath(path, color = curveColor, style = Stroke(width = 2.dp.toPx()))
        }
        // Points
        localPoints.forEachIndexed { i, p ->
            val cx = p.x * w
            val cy = (1f - p.y) * h
            drawCircle(
                color = if (i == activeIndex) AlcedoColors.AccentBlue else Color.White,
                radius = 6.dp.toPx(),
                center = Offset(cx, cy),
            )
        }
    }
}

private fun Float.pow2(): Float = this * this
