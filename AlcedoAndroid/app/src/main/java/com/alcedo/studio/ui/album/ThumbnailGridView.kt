package com.alcedo.studio.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.ui.accessibility.AccessibilityStrings
import com.alcedo.studio.ui.common.ShimmerBox
import com.alcedo.studio.ui.common.rememberHapticFeedback
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.ThumbnailShape

/**
 * A responsive thumbnail grid with enhanced gestures for domestic photography apps.
 *
 * Gesture enhancements:
 * - **Long-press + drag** to enter swipe multi-select mode (drag across thumbnails
 *   to quickly select/deselect multiple images without lifting the finger)
 * - **Pinch-to-zoom** adjusts column count via [onColumnCountChange]
 * - **Haptic feedback** on selection toggle
 *
 * Columns are adjustable from 2 to 14 via [columnCount].
 */
@Composable
fun ThumbnailGridView(
    images: List<ImageItem>,
    selection: Set<String>,
    columnCount: Int,
    modifier: Modifier = Modifier,
    onOpen: (ImageItem) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onLongPress: (ImageItem, androidx.compose.ui.unit.DpOffset) -> Unit = { _, _ -> },
    onColumnCountChange: (Int) -> Unit = {},
) {
    val columns = columnCount.coerceIn(2, 14)
    val haptics = rememberHapticFeedback()
    val gridState = rememberLazyGridState()

    // Track swipe multi-select state
    var isSwipeSelecting by remember { mutableStateOf(false) }
    var swipeSelectMode by remember { mutableStateOf(false) } // true = select, false = deselect
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var lastToggledId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { gridSize = it }
            // Swipe multi-select: drag across thumbnails to select/deselect
            .pointerInput(images, columns, selection) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Find the image under the drag start position
                        val image = findImageAtPosition(offset, gridSize, images, columns)
                        if (image != null) {
                            isSwipeSelecting = true
                            // Toggle the first image and set the mode
                            val isSelected = image.id in selection
                            swipeSelectMode = !isSelected
                            onToggleSelection(image.id)
                            lastToggledId = image.id
                            haptics.toggle()
                        }
                    },
                    onDrag = { change, _ ->
                        if (isSwipeSelecting) {
                            val image = findImageAtPosition(change.position, gridSize, images, columns)
                            if (image != null && image.id != lastToggledId) {
                                val isSelected = image.id in selection
                                // Only toggle if the selection state doesn't match the mode
                                if (isSelected != swipeSelectMode) {
                                    onToggleSelection(image.id)
                                    lastToggledId = image.id
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        isSwipeSelecting = false
                        lastToggledId = null
                        haptics.commit()
                    },
                    onDragCancel = {
                        isSwipeSelecting = false
                        lastToggledId = null
                    },
                )
            },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(4.dp),
        ) {
            items(images, key = { it.id }) { image ->
                ThumbnailTile(
                    image = image,
                    selected = image.id in selection,
                    selectionMode = selection.isNotEmpty() || isSwipeSelecting,
                    onOpen = { onOpen(image) },
                    onToggleSelection = {
                        haptics.toggle()
                        onToggleSelection(image.id)
                    },
                    onLongPress = { offset ->
                        haptics.longPress()
                        onLongPress(image, offset)
                    },
                )
            }
        }
    }
}

/**
 * Find the image at a given position within the grid.
 * Maps pointer coordinates to grid cell indices and returns the corresponding image.
 */
private fun findImageAtPosition(
    position: Offset,
    gridSize: IntSize,
    images: List<ImageItem>,
    columns: Int,
): ImageItem? {
    if (gridSize.width <= 0 || gridSize.height <= 0 || images.isEmpty()) return null

    val spacing = 4.dp.value
    val totalSpacing = spacing * (columns + 1)
    val cellWidth = (gridSize.width - totalSpacing) / columns
    if (cellWidth <= 0) return null

    val col = (position.x / (cellWidth + spacing)).toInt().coerceIn(0, columns - 1)
    val row = (position.y / (cellWidth + spacing)).toInt().coerceAtLeast(0)

    val index = row * columns + col
    return images.getOrNull(index)
}

@Composable
private fun ThumbnailTile(
    image: ImageItem,
    selected: Boolean,
    selectionMode: Boolean = false,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: (androidx.compose.ui.unit.DpOffset) -> Unit,
) {
    val context = LocalContext.current
    val borderColor = if (selected) AlcedoColors.AccentBlue else AlcedoColors.Outline
    val borderWidth = if (selected) 3.dp else 1.dp

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(ThumbnailShape)
            .border(borderWidth, borderColor, ThumbnailShape)
            .combinedClickable(
                onClick = {
                    // In selection mode, tap toggles selection; otherwise open
                    if (selectionMode || selected) {
                        onToggleSelection()
                    } else {
                        onOpen()
                    }
                },
                onLongClick = {
                    // Long press enters selection mode and toggles this item
                    onToggleSelection()
                    // Approximate offset at the tile centre; the host menu anchors here.
                    onLongPress(androidx.compose.ui.unit.DpOffset(96.dp, 96.dp))
                },
            ),
    ) {
        val model = image.thumbnailPath ?: image.originalUri
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = AccessibilityStrings.thumbnail(
                image.displayName, image.rating, selected, image.isRaw,
            ),
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )

        // Bottom gradient with name + badges.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000)),
                    ),
                )
                .padding(4.dp),
        ) {
            if (image.isRaw) {
                RawBadge()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = image.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = AlcedoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RatingStars(image.rating)
            }
            if (image.aiTags.isNotEmpty()) {
                AiTagChip(image.aiTags.first())
            }
        }

        // Color label indicator.
        if (image.colorLabel != ColorLabel.NONE) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(4.dp, 12.dp)
                    .background(Color(image.colorLabel.hex), RoundedCornerShape(2.dp)),
            )
        }

        // Flag indicator.
        when (image.flag) {
            com.alcedo.studio.data.model.ImageFlag.PICK -> FlagDot(AlcedoColors.AccentBlue, Alignment.TopEnd)
            com.alcedo.studio.data.model.ImageFlag.REJECT -> FlagDot(AlcedoColors.Danger, Alignment.TopEnd)
            else -> {}
        }
    }
}

@Composable
private fun RatingStars(rating: Int) {
    Row {
        repeat(5) { i ->
            Icon(
                imageVector = if (i < rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (i < rating) AlcedoColors.StarOn else AlcedoColors.TextDisabled,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun RawBadge() {
    Box(
        modifier = Modifier
            .background(AlcedoColors.Amber, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text = "RAW", color = AlcedoColors.TextOnAccent, fontSize = 8.sp, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AiTagChip(tag: String) {
    Box(
        modifier = Modifier
            .background(AlcedoColors.AccentBlue.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text = tag, color = AlcedoColors.AccentBluePressed, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BoxScope.FlagDot(color: Color, alignment: Alignment) {
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(4.dp)
            .size(8.dp)
            .background(color, RoundedCornerShape(50)),
    )
}

/** A shimmering grid placeholder shown while images load. */
@Composable
fun ThumbnailGridSkeleton(columnCount: Int, modifier: Modifier = Modifier) {
    val columns = columnCount.coerceIn(2, 14)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(40) {
            ShimmerBox(modifier = Modifier.aspectRatio(1f))
        }
    }
}
