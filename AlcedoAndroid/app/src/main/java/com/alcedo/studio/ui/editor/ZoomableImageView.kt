package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Stable
class ZoomableState {
    var scale by mutableFloatStateOf(1f)
        internal set
    var offsetX by mutableFloatStateOf(0f)
        internal set
    var offsetY by mutableFloatStateOf(0f)
        internal set

    private var minScaleInternal = 1f
    private var maxScaleInternal = 4f

    val minScale: Float get() = minScaleInternal
    val maxScale: Float get() = maxScaleInternal

    internal fun setScaleBounds(min: Float, max: Float) {
        minScaleInternal = min
        maxScaleInternal = max
        if (scale < min) scale = min
        if (scale > max) scale = max
    }

    /**
     * 更新缩放 + 平移，并在边界内夹紧。
     * @param fittedSize 经 ContentScale.Fit 计算后（未缩放）图片在容器中的像素尺寸
     * @param containerSize 容器像素尺寸
     */
    fun updateTransform(
        pan: Offset,
        zoom: Float,
        containerSize: IntSize,
        fittedSize: Size,
        centroid: Offset
    ) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return
        if (fittedSize.width <= 0 || fittedSize.height <= 0) return

        val prevScale = scale
        val newScale = (prevScale * zoom).coerceIn(minScaleInternal, maxScaleInternal)

        // 以 centroid 为中心缩放：保持指尖下像素不动
        // centroid 在容器坐标系内；图片左上角（Fit 居中）在:
        val fittedLeft = (containerSize.width - fittedSize.width) / 2f
        val fittedTop = (containerSize.height - fittedSize.height) / 2f

        // 缩放前，目标点（centroid）相对图片左上角的偏移：
        val relXBefore = centroid.x - (fittedLeft + offsetX)
        val relYBefore = centroid.y - (fittedTop + offsetY)

        // 缩放后，该相对偏移乘以 (newScale/prevScale)
        val ratio = newScale / prevScale
        val newOffsetX = centroid.x - (fittedLeft + relXBefore * ratio)
        val newOffsetY = centroid.y - (fittedTop + relYBefore * ratio)

        // 再叠加 pan（在缩放后的坐标系中）：
        val scaledWidth = fittedSize.width * newScale
        val scaledHeight = fittedSize.height * newScale
        val maxOffsetX = if (scaledWidth > containerSize.width) {
            (scaledWidth - containerSize.width) / 2f
        } else 0f
        val maxOffsetY = if (scaledHeight > containerSize.height) {
            (scaledHeight - containerSize.height) / 2f
        } else 0f

        // 注意：offsetX/Y 是图片中心点相对 Fit 居中中心点的偏移。
        // 将 newOffsetX/Y 转换为以容器中心为原点：
        val baseCenterOffsetX = newOffsetX - fittedLeft - (fittedSize.width - containerSize.width) / 2f
        val baseCenterOffsetY = newOffsetY - fittedTop - (fittedSize.height - containerSize.height) / 2f

        scale = newScale
        offsetX = (baseCenterOffsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
        offsetY = (baseCenterOffsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
    }

    fun resetZoom() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    /**
     * 以指定点（容器坐标系）为中心的双击放大/还原。
     */
    fun doubleTapZoom(tap: Offset, containerSize: IntSize, fittedSize: Size) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return
        if (fittedSize.width <= 0 || fittedSize.height <= 0) return
        val targetScale = if (scale > (minScaleInternal + maxScaleInternal) / 2f) {
            minScaleInternal
        } else {
            min(maxScaleInternal, minScaleInternal * 2.5f)
        }
        val prevScale = scale

        val fittedLeft = (containerSize.width - fittedSize.width) / 2f
        val fittedTop = (containerSize.height - fittedSize.height) / 2f

        val relXBefore = tap.x - (fittedLeft + offsetX)
        val relYBefore = tap.y - (fittedTop + offsetY)

        val ratio = targetScale / prevScale
        val newOffsetX = tap.x - (fittedLeft + relXBefore * ratio)
        val newOffsetY = tap.y - (fittedTop + relYBefore * ratio)

        val scaledWidth = fittedSize.width * targetScale
        val scaledHeight = fittedSize.height * targetScale
        val maxOffsetX = if (scaledWidth > containerSize.width) (scaledWidth - containerSize.width) / 2f else 0f
        val maxOffsetY = if (scaledHeight > containerSize.height) (scaledHeight - containerSize.height) / 2f else 0f

        val baseCenterOffsetX = newOffsetX - fittedLeft - (fittedSize.width - containerSize.width) / 2f
        val baseCenterOffsetY = newOffsetY - fittedTop - (fittedSize.height - containerSize.height) / 2f

        scale = targetScale
        offsetX = baseCenterOffsetX.coerceIn(-maxOffsetX, maxOffsetX)
        offsetY = baseCenterOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
    }
}

@Composable
fun rememberZoomableState(): ZoomableState = remember { ZoomableState() }

@Composable
fun ZoomableImageView(
    imageBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    zoomableState: ZoomableState = rememberZoomableState(),
    contentScale: ContentScale = ContentScale.Fit,
    onImageTap: ((Offset) -> Unit)? = null
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var fittedSize by remember { mutableStateOf(Size.Zero) }

    // 当 bitmap / container 变化时：重新计算 fittedSize，复位 scale 边界 + 夹紧当前 transform 到新边界
    LaunchedEffect(imageBitmap, containerSize) {
        val bmp = imageBitmap
        if (bmp != null && containerSize.width > 0 && containerSize.height > 0) {
            val srcSize = Size(bmp.width.toFloat(), bmp.height.toFloat())
            val dstSize = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
            val scale = minOf(
                dstSize.width / srcSize.width,
                dstSize.height / srcSize.height
            )
            fittedSize = Size(srcSize.width * scale, srcSize.height * scale)
            val minS = 1f
            val maxS = 4f
            zoomableState.setScaleBounds(minS, maxS)
            if (zoomableState.scale < minS) zoomableState.scale = minS
            if (zoomableState.scale > maxS) zoomableState.scale = maxS
            // 按新边界重新 clamp offset，避免越界
            val s = zoomableState.scale
            val scaledW = fittedSize.width * s
            val scaledH = fittedSize.height * s
            val maxOffX = if (scaledW > containerSize.width) (scaledW - containerSize.width) / 2f else 0f
            val maxOffY = if (scaledH > containerSize.height) (scaledH - containerSize.height) / 2f else 0f
            zoomableState.offsetX = zoomableState.offsetX.coerceIn(-maxOffX, maxOffX)
            zoomableState.offsetY = zoomableState.offsetY.coerceIn(-maxOffY, maxOffY)
        } else {
            fittedSize = Size.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> containerSize = size },
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null && fittedSize.width > 0f && fittedSize.height > 0f) {
            // 关键修复：按真实 fit 尺寸布局 Image，不再 fillMaxSize，
            // 避免 graphicsLayer 变换把滑块/面板渲染区域错位。
            val widthDp = with(density) { fittedSize.width.toDp() }
            val heightDp = with(density) { fittedSize.height.toDp() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "Edited image",
                modifier = Modifier
                    .size(widthDp, heightDp)
                    .onGloballyPositioned { /* 尺寸已由 dp 控制 */ }
                    .pointerInput(containerSize, fittedSize) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            zoomableState.updateTransform(
                                pan = pan,
                                zoom = zoom,
                                containerSize = containerSize,
                                fittedSize = fittedSize,
                                centroid = centroid
                            )
                        }
                    }
                    .pointerInput(containerSize, fittedSize) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                zoomableState.doubleTapZoom(offset, containerSize, fittedSize)
                            },
                            onTap = { offset ->
                                onImageTap?.invoke(offset)
                            }
                        )
                    }
                    .graphicsLayer {
                        scaleX = zoomableState.scale
                        scaleY = zoomableState.scale
                        translationX = zoomableState.offsetX
                        translationY = zoomableState.offsetY
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                        clip = true
                    },
                contentScale = ContentScale.FillBounds
            )
        }
    }
}
