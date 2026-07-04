package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

@Stable
class ZoomableState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    private var minScale = 0.5f
    private var maxScale = 5f

    fun updateTransform(pan: Offset, zoom: Float, containerSize: IntSize, contentSize: IntSize) {
        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
        scale = newScale

        val scaledWidth = contentSize.width * newScale
        val scaledHeight = contentSize.height * newScale
        val maxOffsetX = max(0f, (scaledWidth - containerSize.width) / 2f)
        val maxOffsetY = max(0f, (scaledHeight - containerSize.height) / 2f)

        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
    }

    fun resetZoom() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun doubleTapZoom() {
        if (scale > 1.5f) {
            resetZoom()
        } else {
            scale = 2.5f
            offsetX = 0f
            offsetY = 0f
        }
    }

    fun fitToContainer(containerSize: IntSize, contentSize: IntSize) {
        if (containerSize.width <= 0 || containerSize.height <= 0) return
        if (contentSize.width <= 0 || contentSize.height <= 0) return

        val widthRatio = containerSize.width.toFloat() / contentSize.width.toFloat()
        val heightRatio = containerSize.height.toFloat() / contentSize.height.toFloat()
        val fitScale = min(widthRatio, heightRatio)

        minScale = fitScale * 0.5f
        maxScale = fitScale * 5f
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
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                containerSize = size
                zoomableState.fitToContainer(size, contentSize)
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Edited image",
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        contentSize = size
                        zoomableState.fitToContainer(containerSize, size)
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            zoomableState.updateTransform(pan, zoom, containerSize, contentSize)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                zoomableState.doubleTapZoom()
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
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
                contentScale = contentScale
            )
        }
    }
}
