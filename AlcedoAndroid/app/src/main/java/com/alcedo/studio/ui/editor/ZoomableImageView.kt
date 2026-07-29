package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.alcedo.studio.ui.common.LoadingOverlay
import com.alcedo.studio.ui.theme.AlcedoColors

/**
 * A zoomable, pannable image viewport. Wraps a [Bitmap] (the live pipeline
 * preview) in a [Box] that interprets pinch-to-zoom and drag gestures via
 * [detectTransformGestures], applying the accumulated scale/translation as a
 * [graphicsLayer].
 *
 * Double-tap resets to fit. When [bitmap] is null a subtle loading overlay is
 * shown so the user knows the pipeline is rendering.
 */
@Composable
fun ZoomableImageView(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    isRendering: Boolean = false,
    onTransform: ((scale: Float, translation: Offset) -> Unit)? = null,
) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.PureBlack)
            .onSizeChanged { viewportSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 8f)
                    scale = newScale
                    if (newScale > 1f && viewportSize != IntSize.Zero) {
                        // Clamp pan so the image cannot be dragged entirely
                        // off-screen: the max travel is half the scaled overflow.
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
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
        if (isRendering && bitmap == null) {
            LoadingOverlay()
        }
    }
}
