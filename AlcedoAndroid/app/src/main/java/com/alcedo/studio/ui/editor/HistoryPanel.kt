package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.EditTransaction
import com.alcedo.studio.data.model.Version
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * History panel. Renders the version tree as a selectable list (active version
 * highlighted) with the ordered [EditTransaction]s of the active version below.
 * Supports creating a virtual copy and deleting a version.
 */
@Composable
fun HistoryPanel(
    versions: List<Version>,
    transactions: List<EditTransaction>,
    activeVersionId: String?,
    onSwitch: (String) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.versions) {
            TextButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = AlcedoColors.AccentBlue)
                Text(s.createVirtualCopy, color = AlcedoColors.AccentBlue)
            }
        }
        if (versions.isEmpty()) {
            EmptyState(title = s.noVersions, modifier = Modifier.padding(DesignTokens.spacingLg))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(versions, key = { it.id }) { version ->
                    VersionRow(
                        version = version,
                        isActive = version.id == activeVersionId,
                        onSwitch = { onSwitch(version.id) },
                        onDelete = { onDelete(version.id) },
                    )
                }
            }

            SectionHeader(title = s.transactions + " (${transactions.size})")
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXxs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(transactions, key = { it.id }) { tx ->
                    TransactionRow(tx)
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    version: Version,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActive) AlcedoColors.SurfaceSelected else AlcedoColors.SurfaceRaised,
                MaterialTheme.shapes.small,
            )
            .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (isActive) AlcedoColors.AccentBlue else AlcedoColors.TextDisabled, CircleShape),
        )
        Text(
            text = version.name + if (version.isVirtualCopy) " (copy)" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) AlcedoColors.TextPrimary else AlcedoColors.TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (version.isVirtualCopy) {
            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = AlcedoColors.TextTertiary)
            }
        }
        TextButton(onClick = onSwitch) {
            Text(if (isActive) "✓" else "→", color = AlcedoColors.AccentBlue)
        }
    }
}

@Composable
private fun TransactionRow(tx: EditTransaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = AlcedoColors.TextTertiary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = tx.label,
            style = MaterialTheme.typography.bodySmall,
            color = AlcedoColors.TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = "${tx.paramDelta.overrides.size}",
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
        )
    }
}
