package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

@Composable
fun CompareView(
    originalBitmap: ImageBitmap?,
    editedBitmap: ImageBitmap?,
    modifier: Modifier = Modifier,
    onCompareStart: () -> Unit = {},
    onCompareEnd: () -> Unit = {}
) {
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var isActive by remember { mutableStateOf(true) }
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = Color.White, fontSize = 12.sp)

    LaunchedEffect(isActive) {
        if (isActive) onCompareStart() else onCompareEnd()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Original image (full background) — "Before"
        if (originalBitmap != null) {
            Image(
                bitmap = originalBitmap,
                contentDescription = "Original",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Layer 2: Edited image clipped to the left of the slider — "After"
        if (editedBitmap != null) {
            Image(
                bitmap = editedBitmap,
                contentDescription = "Edited",
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(0f, 0f, size.width * sliderPosition, size.height) {
                            drawContent()
                        }
                    },
                contentScale = ContentScale.Fit
            )
        }

        // Layer 3: Drag interaction area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        sliderPosition = (sliderPosition + dragAmount / size.width).coerceIn(0f, 1f)
                    }
                }
        )

        // Layer 4: Slider visual (divider line, handle, labels)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val x = size.width * sliderPosition

            // Vertical divider line
            drawLine(
                color = Color.White,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 3f
            )

            // Handle circle — outer ring
            drawCircle(
                color = Color.White,
                radius = 20f,
                center = Offset(x, size.height / 2f)
            )
            // Handle circle — inner fill
            drawCircle(
                color = MaterialTheme.colorScheme.primary,
                radius = 16f,
                center = Offset(x, size.height / 2f)
            )
            // Handle arrows
            drawLine(
                color = Color.White,
                start = Offset(x - 8f, size.height / 2f),
                end = Offset(x + 8f, size.height / 2f),
                strokeWidth = 2f
            )

            // "After" label (bottom-left — where the edited image is visible)
            val afterLayout = textMeasurer.measure("After", textStyle)
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(8f, size.height - 40f),
                size = Size(afterLayout.size.width + 16f, afterLayout.size.height + 8f)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "After",
                topLeft = Offset(16f, size.height - 36f),
                style = textStyle
            )

            // "Before" label (bottom-right — where the original image is visible)
            val beforeLayout = textMeasurer.measure("Before", textStyle)
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(
                    size.width - beforeLayout.size.width - 24f,
                    size.height - 40f
                ),
                size = Size(beforeLayout.size.width + 16f, beforeLayout.size.height + 8f)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "Before",
                topLeft = Offset(
                    size.width - beforeLayout.size.width - 16f,
                    size.height - 36f
                ),
                style = textStyle
            )
        }
    }
}
