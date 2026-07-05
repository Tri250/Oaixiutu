package com.alcedo.studio.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class VersionUiModel(
    val versionId: String,
    val displayName: String,
    val isActive: Boolean = false,
    val transactionCount: Int = 0,
    val createdAt: String = "",
    val lastModified: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersioningPanel(
    versions: List<VersionUiModel>,
    activeVersionId: String,
    onSwitchVersion: (String) -> Unit,
    onCreateVersion: (String) -> Unit,
    onDeleteVersion: (String) -> Unit,
    onCompareVersions: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var compareMode by remember { mutableStateOf(false) }
    var compareTargetId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Versions", style = MaterialTheme.typography.titleSmall)
            Row {
                IconButton(onClick = { compareMode = !compareMode }) {
                    Icon(
                        Icons.Default.Compare,
                        contentDescription = "Compare",
                        tint = if (compareMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Version")
                }
            }
        }

        if (compareMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (compareTargetId == null) "Select a version to compare with active"
                        else "Compare active vs selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        HorizontalDivider()

        // Version list
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(versions, key = { it.versionId }) { version ->
                VersionItem(
                    version = version,
                    isActive = version.versionId == activeVersionId,
                    isCompareMode = compareMode,
                    isCompareTarget = version.versionId == compareTargetId,
                    onSwitch = { onSwitchVersion(version.versionId) },
                    onCompare = {
                        if (compareTargetId == version.versionId) {
                            compareTargetId = null
                        } else {
                            compareTargetId = version.versionId
                            onCompareVersions(activeVersionId, version.versionId)
                        }
                    },
                    onDelete = { showDeleteConfirm = version.versionId }
                )
            }
        }
    }

    // Create version dialog
    if (showCreateDialog) {
        CreateVersionDialog(
            versionCount = versions.size,
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreateVersion(name)
                showCreateDialog = false
            }
        )
    }

    // Delete confirm
    showDeleteConfirm?.let { versionId ->
        val version = versions.find { it.versionId == versionId }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Version") },
            text = { Text("Delete \"${version?.displayName ?: "this version"}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVersion(versionId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VersionItem(
    version: VersionUiModel,
    isActive: Boolean,
    isCompareMode: Boolean,
    isCompareTarget: Boolean,
    onSwitch: () -> Unit,
    onCompare: () -> Unit,
    onDelete: () -> Unit
) {
    val backgroundColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        isCompareTarget -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (isCompareMode) onCompare() else onSwitch() },
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail placeholder
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isActive) Icons.Default.CheckCircle else Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    version.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    Text(
                        "${version.transactionCount} edits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (version.lastModified.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            version.lastModified,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isActive) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "Active",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            if (!isActive && !isCompareMode) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isCompareMode && !isActive) {
                Checkbox(
                    checked = isCompareTarget,
                    onCheckedChange = { onCompare() },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateVersionDialog(
    versionCount: Int,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Version") },
        icon = { Icon(Icons.Default.Layers, contentDescription = null) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Create a new version of this image with its own edit history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Version Name") },
                    placeholder = { Text("Version ${versionCount + 1}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.ifBlank { "New Version" }) },
                enabled = true
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
