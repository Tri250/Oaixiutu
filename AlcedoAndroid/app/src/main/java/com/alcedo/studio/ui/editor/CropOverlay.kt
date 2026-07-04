package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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

data class CropRect(
    val left: Float = 0.1f,
    val top: Float = 0.1f,
    val right: Float = 0.9f,
    val bottom: Float = 0.9f,
    val rotation: Float = 0f
)

enum class AspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ONE_ONE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_TWO("3:2", 3f / 2f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    TWO_ONE("2:1", 2f)
}

@Composable
fun CropOverlay(
    cropRect: CropRect,
    onCropChanged: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: AspectRatio = AspectRatio.FREE,
    showGrid: Boolean = true
) {
    var dragHandle by remember { mutableStateOf(CropHandle.NONE) }
    var initialCropRect by remember { mutableStateOf(cropRect) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(aspectRatio) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        initialCropRect = cropRect
                        val cr = cropRect
                        val handleSize = 40f
                        val left = cr.left * size.width
                        val top = cr.top * size.height
                        val right = cr.right * size.width
                        val bottom = cr.bottom * size.height

                        dragHandle = when {
                            // Corners
                            startOffset.x in (left - handleSize)..(left + handleSize) &&
                                startOffset.y in (top - handleSize)..(top + handleSize) -> CropHandle.TOP_LEFT
                            startOffset.x in (right - handleSize)..(right + handleSize) &&
                                startOffset.y in (top - handleSize)..(top + handleSize) -> CropHandle.TOP_RIGHT
                            startOffset.x in (left - handleSize)..(left + handleSize) &&
                                startOffset.y in (bottom - handleSize)..(bottom + handleSize) -> CropHandle.BOTTOM_LEFT
                            startOffset.x in (right - handleSize)..(right + handleSize) &&
                                startOffset.y in (bottom - handleSize)..(bottom + handleSize) -> CropHandle.BOTTOM_RIGHT
                            // Edges
                            startOffset.y in (top - handleSize)..(top + handleSize) &&
                                startOffset.x in left..right -> CropHandle.TOP
                            startOffset.y in (bottom - handleSize)..(bottom + handleSize) &&
                                startOffset.x in left..right -> CropHandle.BOTTOM
                            startOffset.x in (left - handleSize)..(left + handleSize) &&
                                startOffset.y in top..bottom -> CropHandle.LEFT
                            startOffset.x in (right - handleSize)..(right + handleSize) &&
                                startOffset.y in top..bottom -> CropHandle.RIGHT
                            // Inside
                            startOffset.x in left..right && startOffset.y in top..bottom -> CropHandle.MOVE
                            else -> CropHandle.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (dragHandle == CropHandle.NONE) return@detectDragGestures
                        val dx = dragAmount.x / size.width
                        val dy = dragAmount.y / size.height
                        var newCrop = updateCropRect(initialCropRect, dragHandle, dx, dy, aspectRatio)

                        // Clamp
                        newCrop = newCrop.copy(
                            left = newCrop.left.coerceIn(0f, newCrop.right - 0.05f),
                            top = newCrop.top.coerceIn(0f, newCrop.bottom - 0.05f),
                            right = newCrop.right.coerceIn(newCrop.left + 0.05f, 1f),
                            bottom = newCrop.bottom.coerceIn(newCrop.top + 0.05f, 1f)
                        )

                        onCropChanged(newCrop)
                    },
                    onDragEnd = { dragHandle = CropHandle.NONE }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, rotation ->
                    if (rotation != 0f) {
                        val newRotation = (cropRect.rotation + rotation) % 360f
                        onCropChanged(cropRect.copy(rotation = newRotation))
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val left = cropRect.left * w
        val top = cropRect.top * h
        val right = cropRect.right * w
        val bottom = cropRect.bottom * h

        // Dimming outside
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            size = size
        )

        // Clear crop area
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            blendMode = BlendMode.Clear
        )

        // Crop border
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 2f)
        )

        // Rule of thirds grid
        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.3f)
            for (i in 1..2) {
                val gx = left + (right - left) * i / 3f
                val gy = top + (bottom - top) * i / 3f
                drawLine(gridColor, Offset(gx, top), Offset(gx, bottom), strokeWidth = 0.5f)
                drawLine(gridColor, Offset(left, gy), Offset(right, gy), strokeWidth = 0.5f)
            }
        }

        // Handles
        val handleSize = 24f
        val handleColor = Color.White
        val handleStroke = 2f

        listOf(
            Offset(left, top), Offset(right, top),
            Offset(left, bottom), Offset(right, bottom),
            Offset((left + right) / 2, top), Offset((left + right) / 2, bottom),
            Offset(left, (top + bottom) / 2), Offset(right, (top + bottom) / 2)
        ).forEach { pos ->
            drawCircle(
                color = handleColor,
                radius = handleSize / 2,
                center = pos,
                style = Stroke(width = handleStroke)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = handleSize / 2 - 2f,
                center = pos
            )
        }
    }
}

private fun updateCropRect(
    crop: CropRect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    aspectRatio: AspectRatio
): CropRect {
    var newCrop = when (handle) {
        CropHandle.TOP_LEFT -> crop.copy(left = crop.left + dx, top = crop.top + dy)
        CropHandle.TOP_RIGHT -> crop.copy(right = crop.right + dx, top = crop.top + dy)
        CropHandle.BOTTOM_LEFT -> crop.copy(left = crop.left + dx, bottom = crop.bottom + dy)
        CropHandle.BOTTOM_RIGHT -> crop.copy(right = crop.right + dx, bottom = crop.bottom + dy)
        CropHandle.TOP -> crop.copy(top = crop.top + dy)
        CropHandle.BOTTOM -> crop.copy(bottom = crop.bottom + dy)
        CropHandle.LEFT -> crop.copy(left = crop.left + dx)
        CropHandle.RIGHT -> crop.copy(right = crop.right + dx)
        CropHandle.MOVE -> crop.copy(
            left = crop.left + dx,
            right = crop.right + dx,
            top = crop.top + dy,
            bottom = crop.bottom + dy
        )
        CropHandle.NONE -> crop
    }

    // Enforce aspect ratio
    val targetRatio = aspectRatio.ratio
    if (targetRatio != null) {
        val currentWidth = newCrop.right - newCrop.left
        val currentHeight = newCrop.bottom - newCrop.top
        if (currentWidth > 0 && currentHeight > 0) {
            val currentRatio = currentWidth / currentHeight
            if (currentRatio != targetRatio) {
                when (handle) {
                    CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT,
                    CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT -> {
                        val newWidth = currentHeight * targetRatio
                        val dw = (newWidth - currentWidth) / 2f
                        newCrop = newCrop.copy(
                            left = newCrop.left - dw,
                            right = newCrop.right + dw
                        )
                    }
                    CropHandle.TOP, CropHandle.BOTTOM -> {
                        val newWidth = currentHeight * targetRatio
                        val dw = (newWidth - currentWidth) / 2f
                        newCrop = newCrop.copy(
                            left = newCrop.left - dw,
                            right = newCrop.right + dw
                        )
                    }
                    CropHandle.LEFT, CropHandle.RIGHT -> {
                        val newHeight = currentWidth / targetRatio
                        val dh = (newHeight - currentHeight) / 2f
                        newCrop = newCrop.copy(
                            top = newCrop.top - dh,
                            bottom = newCrop.bottom + dh
                        )
                    }
                    CropHandle.MOVE, CropHandle.NONE -> {}
                }
            }
        }
    }

    return newCrop
}

private enum class CropHandle {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT
}