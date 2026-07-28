package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.alcedo.studio.ui.theme.AlcedoColors

/**
 * Before/after comparison slider. Renders [afterBitmap] full-bleed and clips
 * [beforeBitmap] to the left of a draggable divider. The divider position is a
 * 0..1 fraction of the viewport width; dragging updates it live.
 *
 * If either bitmap is null the available one is shown full-bleed.
 */
@Composable
fun CompareView(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    var split by remember { mutableFloatStateOf(0.5f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.PureBlack)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    split = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    split = (offset.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        // After (full)
        if (afterBitmap != null) {
            Image(
                bitmap = afterBitmap.asImageBitmap(),
                contentDescription = "After",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Before (clipped to left of divider)
        if (beforeBitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width * split
                clipRect(left = 0f, top = 0f, right = w, bottom = size.height, clipOp = ClipOp.Intersect) {
                    drawImage(
                        image = beforeBitmap.asImageBitmap(),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    )
                }
                // Divider line
                drawLine(
                    color = AlcedoColors.AccentBlue,
                    start = Offset(w, 0f),
                    end = Offset(w, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
                // Handle
                drawCircle(
                    color = AlcedoColors.AccentBlue,
                    radius = 10.dp.toPx(),
                    center = Offset(w, size.height / 2),
                )
            }
        }
    }
}
