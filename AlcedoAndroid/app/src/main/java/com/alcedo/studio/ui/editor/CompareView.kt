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
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.theme.AlcedoSpacing

enum class CompareMode(val label: String) {
    SPLIT("Split"),
    ONION_SKIN("Overlay"),
    SIDE_BY_SIDE("Parallel")
}

@Composable
fun CompareView(
    originalImage: ImageBitmap?,
    editedImage: ImageBitmap?,
    compareMode: CompareMode,
    modifier: Modifier = Modifier,
    overlayOpacity: Float = 0.5f,
    onOverlayOpacityChange: ((Float) -> Unit)? = null
) {
    if (originalImage == null || editedImage == null) {
        // Render a placeholder instead of empty layout
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringRes { compareOriginal },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var splitPosition by remember { mutableFloatStateOf(0.5f) }
    var localOpacity by remember { mutableFloatStateOf(overlayOpacity) }
    val effectiveOpacity = if (onOverlayOpacityChange != null) overlayOpacity else localOpacity
    val accCompareDesc = stringRes { accCompareView }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
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
                    stateDescription = when (compareMode) {
                        CompareMode.SPLIT -> "Split at ${(splitPosition * 100).toInt()}%"
                        CompareMode.ONION_SKIN -> "Overlay opacity ${(effectiveOpacity * 100).toInt()}%"
                        CompareMode.SIDE_BY_SIDE -> "Side by side"
                    }
                }
                .pointerInput(compareMode) {
                    when (compareMode) {
                        CompareMode.SPLIT -> {
                            detectDragGestures { change, _ ->
                                splitPosition = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                        CompareMode.ONION_SKIN -> {
                            detectDragGestures { change, _ ->
                                val newOpacity = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                if (onOverlayOpacityChange != null) {
                                    onOverlayOpacityChange(newOpacity)
                                } else {
                                    localOpacity = newOpacity
                                }
                            }
                        }
                        CompareMode.SIDE_BY_SIDE -> {
                            detectTapGestures()
                        }
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
                    // 叠加模式：先绘制原图，再以可调透明度叠加编辑后图
                    drawImage(
                        image = originalImage,
                        dstSize = dstIntSize
                    )
                    drawImage(
                        image = editedImage,
                        dstSize = dstIntSize,
                        alpha = effectiveOpacity
                    )
                }
                CompareMode.SIDE_BY_SIDE -> {
                    // 并排模式：左右两张完整图，使用统一的尺寸避免变形
                    val halfWidth = imageWidth / 2f
                    clipRect(0f, 0f, halfWidth, imageHeight) {
                        drawImage(
                            image = originalImage,
                            dstSize = IntSize(halfWidth.toInt(), imageHeight.toInt())
                        )
                    }
                    clipRect(halfWidth, 0f, imageWidth, imageHeight) {
                        drawImage(
                            image = editedImage,
                            srcSize = IntSize(editedImage.width, editedImage.height),
                            dstSize = IntSize(halfWidth.toInt(), imageHeight.toInt()),
                            dstOffset = androidx.compose.ui.unit.IntOffset(halfWidth.toInt(), 0)
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

        // 控制提示 / 标签
        when (compareMode) {
            CompareMode.SPLIT -> {
                Text(
                    "Drag to split · ${(splitPosition * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CompareMode.ONION_SKIN -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Drag vertically to adjust opacity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Opacity ${(effectiveOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            CompareMode.SIDE_BY_SIDE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
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
                }
            }
        }
    }
}

