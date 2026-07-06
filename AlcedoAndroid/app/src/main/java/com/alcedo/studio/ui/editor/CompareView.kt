package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes

enum class CompareMode {
    SPLIT,
    ONION_SKIN,
    SIDE_BY_SIDE
}

@Composable
fun CompareView(
    originalImage: ImageBitmap?,
    editedImage: ImageBitmap?,
    compareMode: CompareMode,
    modifier: Modifier = Modifier
) {
    if (originalImage == null || editedImage == null) return

    var splitPosition by remember { mutableFloatStateOf(0.5f) }
    val accCompareDesc = stringRes { accCompareView }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringRes { compareOriginal },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringRes { compareEdited },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Compare canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    originalImage.width.toFloat() / originalImage.height.toFloat()
                )
                .semantics {
                    contentDescription = accCompareDesc
                    stateDescription = "Split at ${(splitPosition * 100).toInt()}%"
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        splitPosition = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
        ) {
            val imageWidth = size.width
            val imageHeight = size.height
            val dstIntSize = IntSize(imageWidth.toInt(), imageHeight.toInt())

            when (compareMode) {
                CompareMode.SPLIT -> {
                    // Draw edited image first (full)
                    drawImage(
                        image = editedImage,
                        dstSize = dstIntSize
                    )
                    // Draw original on the left side
                    clipRect(0f, 0f, imageWidth * splitPosition, imageHeight) {
                        drawImage(
                            image = originalImage,
                            dstSize = dstIntSize
                        )
                    }
                    // Draw split line
                    drawLine(
                        color = Color.White,
                        start = Offset(imageWidth * splitPosition, 0f),
                        end = Offset(imageWidth * splitPosition, imageHeight),
                        strokeWidth = 2f,
                        blendMode = BlendMode.SrcOver
                    )
                }
                CompareMode.ONION_SKIN -> {
                    drawImage(
                        image = originalImage,
                        dstSize = dstIntSize
                    )
                    drawImage(
                        image = editedImage,
                        dstSize = dstIntSize,
                        alpha = 1f - splitPosition
                    )
                }
                CompareMode.SIDE_BY_SIDE -> {
                    val halfWidth = imageWidth / 2f
                    clipRect(0f, 0f, halfWidth, imageHeight) {
                        drawImage(
                            image = originalImage,
                            dstSize = dstIntSize
                        )
                    }
                    clipRect(halfWidth, 0f, imageWidth, imageHeight) {
                        drawImage(
                            image = editedImage,
                            dstSize = dstIntSize
                        )
                    }
                    // Divider line
                    drawLine(
                        color = Color.White,
                        start = Offset(halfWidth, 0f),
                        end = Offset(halfWidth, imageHeight),
                        strokeWidth = 2f,
                        blendMode = BlendMode.SrcOver
                    )
                }
            }
        }

        // Labels below
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (compareMode == CompareMode.SIDE_BY_SIDE)
                Arrangement.SpaceBetween else Arrangement.Center
        ) {
            if (compareMode == CompareMode.SIDE_BY_SIDE) {
                Text(
                    stringRes { compareBefore },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringRes { compareAfter },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${(splitPosition * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

