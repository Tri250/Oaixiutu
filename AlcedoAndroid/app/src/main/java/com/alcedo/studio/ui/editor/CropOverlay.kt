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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes
import kotlin.math.*

data class CropState(
    val left: Float = 0f,    // 0-1 相对于图片
    val top: Float = 0f,     // 0-1 相对于图片
    val right: Float = 1f,   // 0-1 相对于图片
    val bottom: Float = 1f,  // 0-1 相对于图片
    val rotation: Int = 0,   // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val aspectRatio: CropAspectRatio = CropAspectRatio.FREE
)

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("自由", null),
    RATIO_1_1("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_3_2("3:2", 3f / 2f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_2_3("2:3", 2f / 3f),
    RATIO_9_16("9:16", 9f / 16f)
}

@Composable
fun CropOverlay(
    cropState: CropState,
    onCropStateChanged: (CropState) -> Unit,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    compositionOverlay: CompositionOverlayType = CompositionOverlayType.NONE
) {
    var dragHandle by remember { mutableStateOf(CropHandle.NONE) }
    var initialCropState by remember { mutableStateOf(cropState) }
    val accCropOverlayDesc = stringRes { accCropOverlay }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = accCropOverlayDesc
                stateDescription = "Crop: L=${(cropState.left * 100).toInt()}%, T=${(cropState.top * 100).toInt()}%, R=${(cropState.right * 100).toInt()}%, B=${(cropState.bottom * 100).toInt()}%"
                role = Role.Image
            }
            .pointerInput(cropState.aspectRatio) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        initialCropState = cropState
                        val cr = cropState
                        val hitSize = 48f
                        val left = cr.left * size.width
                        val top = cr.top * size.height
                        val right = cr.right * size.width
                        val bottom = cr.bottom * size.height
                        val midX = (left + right) / 2f
                        val midY = (top + bottom) / 2f

                        dragHandle = when {
                            // 四角手柄（8dp 交互区域放大到 48f）
                            startOffset.x in (left - hitSize)..(left + hitSize) &&
                                startOffset.y in (top - hitSize)..(top + hitSize) -> CropHandle.TOP_LEFT
                            startOffset.x in (right - hitSize)..(right + hitSize) &&
                                startOffset.y in (top - hitSize)..(top + hitSize) -> CropHandle.TOP_RIGHT
                            startOffset.x in (left - hitSize)..(left + hitSize) &&
                                startOffset.y in (bottom - hitSize)..(bottom + hitSize) -> CropHandle.BOTTOM_LEFT
                            startOffset.x in (right - hitSize)..(right + hitSize) &&
                                startOffset.y in (bottom - hitSize)..(bottom + hitSize) -> CropHandle.BOTTOM_RIGHT
                            // 四边中点手柄
                            startOffset.y in (top - hitSize)..(top + hitSize) &&
                                startOffset.x in (midX - hitSize)..(midX + hitSize) -> CropHandle.TOP
                            startOffset.y in (bottom - hitSize)..(bottom + hitSize) &&
                                startOffset.x in (midX - hitSize)..(midX + hitSize) -> CropHandle.BOTTOM
                            startOffset.x in (left - hitSize)..(left + hitSize) &&
                                startOffset.y in (midY - hitSize)..(midY + hitSize) -> CropHandle.LEFT
                            startOffset.x in (right - hitSize)..(right + hitSize) &&
                                startOffset.y in (midY - hitSize)..(midY + hitSize) -> CropHandle.RIGHT
                            // 裁剪区域内移动
                            startOffset.x in left..right && startOffset.y in top..bottom -> CropHandle.MOVE
                            else -> CropHandle.NONE
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (dragHandle == CropHandle.NONE) return@detectDragGestures
                        val dx = dragAmount.x / size.width
                        val dy = dragAmount.y / size.height
                        var newCrop = updateCropState(initialCropState, dragHandle, dx, dy, cropState.aspectRatio)

                        // Clamp
                        newCrop = newCrop.copy(
                            left = newCrop.left.coerceIn(0f, newCrop.right - 0.05f),
                            top = newCrop.top.coerceIn(0f, newCrop.bottom - 0.05f),
                            right = newCrop.right.coerceIn(newCrop.left + 0.05f, 1f),
                            bottom = newCrop.bottom.coerceIn(newCrop.top + 0.05f, 1f)
                        )

                        onCropStateChanged(newCrop)
                    },
                    onDragEnd = { dragHandle = CropHandle.NONE }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, rotation ->
                    if (rotation != 0f) {
                        val newRotation = (cropState.rotation + rotation.toInt()) % 360
                        onCropStateChanged(cropState.copy(rotation = newRotation))
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val left = cropState.left * w
        val top = cropState.top * h
        val right = cropState.right * w
        val bottom = cropState.bottom * h

        // 半透明黑色蒙版 — 裁剪区域外
        // 上方
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset.Zero,
            size = Size(w, top)
        )
        // 下方
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(0f, bottom),
            size = Size(w, h - bottom)
        )
        // 左方
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(0f, top),
            size = Size(left, bottom - top)
        )
        // 右方
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(right, top),
            size = Size(w - right, bottom - top)
        )

        // 裁剪边框
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 2f)
        )

        // 三分法网格线
        if (compositionOverlay != CompositionOverlayType.NONE) {
            drawCompositionOverlay(compositionOverlay, Rect(left, top, right, bottom))
        } else if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.3f)
            for (i in 1..2) {
                val gx = left + (right - left) * i / 3f
                val gy = top + (bottom - top) * i / 3f
                drawLine(gridColor, Offset(gx, top), Offset(gx, bottom), strokeWidth = 0.5f)
                drawLine(gridColor, Offset(left, gy), Offset(right, gy), strokeWidth = 0.5f)
            }
        }

        // 四角手柄 — 8dp 圆形
        val cornerHandleRadius = 8.dp.toPx()
        val edgeHandleRadius = 6.dp.toPx()

        listOf(
            Offset(left, top), Offset(right, top),
            Offset(left, bottom), Offset(right, bottom)
        ).forEach { pos ->
            drawCircle(
                color = Color.White,
                radius = cornerHandleRadius,
                center = pos
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = cornerHandleRadius - 2f,
                center = pos
            )
        }

        // 四边中点手柄
        listOf(
            Offset((left + right) / 2, top),
            Offset((left + right) / 2, bottom),
            Offset(left, (top + bottom) / 2),
            Offset(right, (top + bottom) / 2)
        ).forEach { pos ->
            drawCircle(
                color = Color.White,
                radius = edgeHandleRadius,
                center = pos
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = edgeHandleRadius - 2f,
                center = pos
            )
        }
    }
}

private fun updateCropState(
    crop: CropState,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    aspectRatio: CropAspectRatio
): CropState {
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
    if (targetRatio != null && targetRatio > 0.001f) {
        val currentWidth = newCrop.right - newCrop.left
        val currentHeight = newCrop.bottom - newCrop.top
        if (currentWidth > 0.001f && currentHeight > 0.001f) {
            val currentRatio = currentWidth / currentHeight
            if (abs(currentRatio - targetRatio) > 0.001f) {
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

// ── Legacy compatibility types ──────────────────────────────────────────

data class CropRect(
    val left: Float = 0.1f,
    val top: Float = 0.1f,
    val right: Float = 0.9f,
    val bottom: Float = 0.9f,
    val rotation: Float = 0f
)

enum class AspectRatio(val ratio: Float?) {
    FREE(null),
    ONE_ONE(1f),
    FOUR_THREE(4f / 3f),
    THREE_TWO(3f / 2f),
    SIXTEEN_NINE(16f / 9f),
    TWO_ONE(2f),
    THREE_ONE(3f),
    NINE_SIXTEEN(9f / 16f);

    @androidx.compose.runtime.Composable
    fun label(): String = when (this) {
        FREE -> stringRes { ratioFree }
        ONE_ONE -> stringRes { ratio1_1 }
        FOUR_THREE -> stringRes { ratio4_3 }
        THREE_TWO -> stringRes { ratio3_2 }
        SIXTEEN_NINE -> stringRes { ratio16_9 }
        TWO_ONE -> stringRes { ratio2_1 }
        THREE_ONE -> stringRes { ratio3_1 }
        NINE_SIXTEEN -> stringRes { ratio9_16 }
    }
}

private enum class CropHandle {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT
}
