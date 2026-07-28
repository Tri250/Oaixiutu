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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.PureBlack)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 8f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
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
