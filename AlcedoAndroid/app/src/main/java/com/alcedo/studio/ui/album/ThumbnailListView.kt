package com.alcedo.studio.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoMonoStyle
import com.alcedo.studio.ui.theme.DesignTokens
import com.alcedo.studio.ui.theme.ThumbnailShape

/**
 * A list view of the album's images. Each row shows a square thumbnail,
 * filename, EXIF metadata summary and a rating star row. Long-press opens the
 * context menu; tap opens the editor (or toggles selection when already part of
 * a multi-selection).
 */
@Composable
fun ThumbnailListView(
    images: List<ImageItem>,
    selection: Set<String>,
    modifier: Modifier = Modifier,
    onOpen: (ImageItem) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onLongPress: (ImageItem, androidx.compose.ui.unit.DpOffset) -> Unit = { _, _ -> },
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(images, key = { it.id }) { image ->
            ThumbnailListRow(
                image = image,
                selected = image.id in selection,
                anySelected = selection.isNotEmpty(),
                onOpen = { onOpen(image) },
                onToggleSelection = { onToggleSelection(image.id) },
                onLongPress = { onLongPress(image, androidx.compose.ui.unit.DpOffset(120.dp, 24.dp)) },
            )
        }
    }
}

@Composable
private fun ThumbnailListRow(
    image: ImageItem,
    selected: Boolean,
    anySelected: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val bgColor = if (selected) AlcedoColors.SurfaceSelected else AlcedoColors.SurfaceRaised
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = { if (anySelected) onToggleSelection() else onOpen() },
                onLongClick = { onToggleSelection(); onLongPress() },
            )
            .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        val model = image.thumbnailPath ?: image.originalUri
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
            contentDescription = image.displayName,
            modifier = Modifier
                .size(56.dp)
                .clip(ThumbnailShape)
                .border(1.dp, if (selected) AlcedoColors.AccentBlue else AlcedoColors.Outline, ThumbnailShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (image.isRaw) {
                    Box(
                        modifier = Modifier
                            .background(AlcedoColors.Amber, RoundedCornerShape(2.dp))
                            .padding(horizontal = 3.dp, vertical = 0.dp),
                    ) { Text("RAW", color = AlcedoColors.TextOnAccent, style = MaterialTheme.typography.labelSmall) }
                    Box(modifier = Modifier.size(DesignTokens.spacingXs))
                }
                Text(
                    text = image.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlcedoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = buildString {
                image.cameraModel?.let { append(it) }
                if (image.focalLength != null) {
                    if (isNotEmpty()) append(" · ")
                    append("${image.focalLength.toInt()}mm")
                }
                if (image.aperture != null) {
                    if (isNotEmpty()) append(" · ")
                    append("f/${image.aperture}")
                }
                if (image.iso != null) {
                    if (isNotEmpty()) append(" · ")
                    append("ISO ${image.iso}")
                }
            }
            if (meta.isNotEmpty()) {
                Text(text = meta, style = AlcedoMonoStyle, color = AlcedoColors.TextTertiary, maxLines = 1)
            }
        }
        // Rating + flag.
        Column(horizontalAlignment = Alignment.End) {
            Row {
                repeat(5) { i ->
                    Icon(
                        imageVector = if (i < image.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        tint = if (i < image.rating) AlcedoColors.StarOn else AlcedoColors.TextDisabled,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            when (image.flag) {
                ImageFlag.PICK -> Text("PICK", style = MaterialTheme.typography.labelSmall, color = AlcedoColors.AccentBlue)
                ImageFlag.REJECT -> Text("REJECT", style = MaterialTheme.typography.labelSmall, color = AlcedoColors.Danger)
                else -> {}
            }
        }
        // Color label swatch.
        if (image.colorLabel != ColorLabel.NONE) {
            Box(
                modifier = Modifier
                    .size(4.dp, 24.dp)
                    .background(Color(image.colorLabel.hex), RoundedCornerShape(2.dp)),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AlcedoColors.Divider),
    )
}
