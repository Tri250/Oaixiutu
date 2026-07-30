package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.common.LoadingOverlay
import com.alcedo.studio.ui.theme.AlcedoColors

/**
 * 增强版可缩放图像视图 — 参考醒图/Lightroom Mobile的交互模式：
 * - 双指捏合缩放 (0.5x~8x)
 * - 双击快速复位到适配视图
 * - **长按显示原图** (松手恢复，模拟国内App的对比交互)
 * - 支持回调通知变换状态
 *
 * @param bitmap        当前预览图（处理后）
 * @param beforeBitmap  原图（用于长按对比），为null则不启用长按对比
 * @param modifier      修饰符
 * @param isRendering   是否正在渲染
 * @param onLongPressCompare 长按状态变化回调(true=显示原图, false=恢复)
 * @param onTransform   变换状态回调
 * @param onSwipeLeft   左滑回调（切换下一张）
 * @param onSwipeRight  右滑回调（切换上一张）
 */
@Composable
fun ZoomableImageView(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    isRendering: Boolean = false,
    beforeBitmap: Bitmap? = null,
    onLongPressCompare: ((Boolean) -> Unit)? = null,
    onTransform: ((scale: Float, translation: Offset) -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isLongPressing by remember { mutableStateOf(false) }

    // 决定显示哪个bitmap：长按时显示原图，否则显示当前图
    val displayBitmap = if (isLongPressing && beforeBitmap != null) beforeBitmap else bitmap

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.PureBlack)
            .onSizeChanged { viewportSize = it }
            // 双指缩放/拖拽
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 8f)
                    scale = newScale
                    if (newScale > 1f && viewportSize != IntSize.Zero) {
                        val maxX = (viewportSize.width * (newScale - 1f)) / 2f
                        val maxY = (viewportSize.height * (newScale - 1f)) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    onTransform?.invoke(scale, Offset(offsetX, offsetY))
                }
            }
            // 单击/双击/长按
            .pointerInput(beforeBitmap) {
                detectTapGestures(
                    onDoubleTap = {
                        // 双击复位
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onLongPress = {
                        // 长按显示原图（对比）
                        if (beforeBitmap != null) {
                            isLongPressing = true
                            onLongPressCompare?.invoke(true)
                        }
                    },
                    onTap = {
                        // 长按结束（由系统的释放触发）
                        if (isLongPressing) {
                            isLongPressing = false
                            onLongPressCompare?.invoke(false)
                        }
                    },
                )
            }
            // 释放长按
            .pointerInput(isLongPressing) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (isLongPressing && event.changes.all { !it.pressed }) {
                            isLongPressing = false
                            onLongPressCompare?.invoke(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (displayBitmap != null) {
            Image(
                bitmap = displayBitmap.asImageBitmap(),
                contentDescription = "Preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
            )
        }

        // 长按对比指示器
        if (isLongPressing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(
                        AlcedoColors.SurfaceScrim,
                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                androidx.compose.material3.Text(
                    text = "原图",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = AlcedoColors.TextPrimary,
                )
            }
        }

        if (isRendering && bitmap == null) {
            LoadingOverlay()
        }
    }
}