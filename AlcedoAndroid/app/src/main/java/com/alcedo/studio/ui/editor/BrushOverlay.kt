package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.alcedo.studio.ui.theme.AlcedoColors

/**
 * Brush mask overlay drawing on Canvas. Touch to paint mask strokes.
 * Shows a brush cursor circle that follows the finger, and supports undo
 * via a stroke history stack.
 */
@Composable
fun BrushOverlay(
    brushRadius: Float = 20f,
    brushHardness: Float = 0.8f,
    brushFlow: Float = 1f,
    modifier: Modifier = Modifier,
    onStroke: (List<Offset>) -> Unit = {},
    onUndo: () -> Unit = {},
) {
    var cursorPos by remember { mutableStateOf(Offset.Unspecified) }
    var isDrawing by remember { mutableStateOf(false) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val strokeHistory = remember { mutableListOf<List<Offset>>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDrawing = true
                        currentStroke = listOf(offset)
                        cursorPos = offset
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        cursorPos = change.position
                        currentStroke = currentStroke + change.position
                    },
                    onDragEnd = {
                        if (currentStroke.isNotEmpty()) {
                            strokeHistory.add(currentStroke)
                            onStroke(currentStroke)
                        }
                        isDrawing = false
                        currentStroke = emptyList()
                    },
                    onDragCancel = {
                        isDrawing = false
                        currentStroke = emptyList()
                    },
                )
            },
    ) {
        // Draw existing strokes
        val maskColor = AlcedoColors.AccentBlue.copy(alpha = 0.3f)
        strokeHistory.forEach { stroke ->
            if (stroke.size >= 2) {
                val path = Path()
                path.moveTo(stroke.first().x, stroke.first().y)
                for (i in 1 until stroke.size) {
                    path.lineTo(stroke[i].x, stroke[i].y)
                }
                drawPath(
                    path = path,
                    color = maskColor,
                    style = Stroke(width = brushRadius * 2f * density),
                )
            } else if (stroke.size == 1) {
                drawCircle(maskColor, radius = brushRadius * density, center = stroke.first())
            }
        }

        // Draw current active stroke
        if (isDrawing && currentStroke.size >= 2) {
            val path = Path()
            path.moveTo(currentStroke.first().x, currentStroke.first().y)
            for (i in 1 until currentStroke.size) {
                path.lineTo(currentStroke[i].x, currentStroke[i].y)
            }
            drawPath(
                path = path,
                color = maskColor,
                style = Stroke(width = brushRadius * 2f * density),
            )
        }

        // Brush cursor
        if (cursorPos != Offset.Unspecified) {
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = brushRadius * density,
                center = cursorPos,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            // Hardness indicator
            if (brushHardness < 1f) {
                val innerRadius = brushRadius * brushHardness * density
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = innerRadius,
                    center = cursorPos,
                    style = Stroke(width = 0.5.dp.toPx()),
                )
            }
            // Center dot
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 2.dp.toPx(),
                center = cursorPos,
            )
        }
    }
}
