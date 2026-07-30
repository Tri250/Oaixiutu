package com.alcedo.studio.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.SleeveFolder
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Folder-tree sidebar. Renders a flat list of collections (folders) with
 * expand/collapse chevrons, a create-folder button and an import button. The
 * host owns the tree and feeds [folders] (already expanded to visible depth);
 * selection drives the album's current folder filter.
 */
@Composable
fun CollectionsPanel(
    folders: List<SleeveFolder>,
    selectedPath: String?,
    modifier: Modifier = Modifier,
    onSelectFolder: (String?) -> Unit = {},
    onCreateFolder: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val s = Strings.res
    Column(
        modifier = modifier
            .background(AlcedoColors.Graphite)
            .padding(vertical = DesignTokens.spacingSm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.collections,
                style = MaterialTheme.typography.labelMedium,
                color = AlcedoColors.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCreateFolder, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = s.createFolder, tint = AlcedoColors.TextSecondary)
            }
        }
        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingXs),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(s.import, modifier = Modifier.padding(start = DesignTokens.spacingXs))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(folders, key = { it.id }) { folder ->
                FolderRow(
                    folder = folder,
                    selected = folder.sleevePath == selectedPath,
                    onClick = { onSelectFolder(folder.sleevePath) },
                )
            }
            item {
                FolderRow(
                    folder = null,
                    selected = selectedPath == null,
                    onClick = { onSelectFolder(null) },
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: SleeveFolder?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val s = Strings.res
    val bg = if (selected) AlcedoColors.SurfaceSelected else androidx.compose.ui.graphics.Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        val icon = if (selected) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) AlcedoColors.AccentBlue else AlcedoColors.TextTertiary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = folder?.name ?: s.allPhotos,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) AlcedoColors.TextPrimary else AlcedoColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        folder?.imageCount?.let { count ->
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = AlcedoColors.TextTertiary,
                )
            }
        }
    }
}
