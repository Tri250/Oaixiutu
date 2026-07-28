package com.alcedo.studio.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.ui.accessibility.AccessibilityStrings
import com.alcedo.studio.ui.common.ShimmerBox
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.ThumbnailShape

/**
 * A responsive thumbnail grid. Columns are adjustable from 2 to 14 via
 * [columnCount]. Each tile shows the image (via Coil), a rating star row, AI
 * tag chips, RAW badge and a selection highlight. Long-press opens the context
 * menu; tap toggles selection or opens the editor.
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
) {
    val columns = columnCount.coerceIn(2, 14)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(images, key = { it.id }) { image ->
            ThumbnailTile(
                image = image,
                selected = image.id in selection,
                onOpen = { onOpen(image) },
                onToggleSelection = { onToggleSelection(image.id) },
                onLongPress = { offset -> onLongPress(image, offset) },
            )
        }
    }
}

@Composable
private fun ThumbnailTile(
    image: ImageItem,
    selected: Boolean,
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
                    if (selected) onToggleSelection() else onOpen()
                },
                onLongClick = {
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
