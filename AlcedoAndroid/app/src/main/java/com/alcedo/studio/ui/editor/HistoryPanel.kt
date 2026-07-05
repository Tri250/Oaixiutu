package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.EditHistory
import com.alcedo.studio.data.model.Version
import com.alcedo.studio.data.model.generateHash128
import java.time.format.DateTimeFormatter

@Composable
fun HistoryPanel(
    history: EditHistory?,
    onSwitchVersion: (String) -> Unit,
    onCreateVersion: (String) -> Unit,
    onDeleteVersion: (String) -> Unit,
    onRenameVersion: (String, String) -> Unit,
    onCloneHistory: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newVersionName by remember { mutableStateOf("") }
    var renamingVersionId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    val versions = history?.versionStorage?.values?.toList() ?: emptyList()
    val activeVersionId = history?.activeVersionId ?: ""
    val versionOrder = history?.versionOrder ?: emptyList()

    val orderedVersions = versionOrder.mapNotNull { node ->
        versions.find { it.versionId == node.versionId }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Versions (${orderedVersions.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                IconButton(onClick = { onUndo() }) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = { onRedo() }) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo")
                }
                IconButton(onClick = onCloneHistory) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Clone History")
                }
            }
        }

        Button(
            onClick = {
                newVersionName = ""
                showCreateDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Version")
        }

        // Version list
        orderedVersions.forEach { version ->
            val isActive = version.versionId == activeVersionId
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface
                ),
                onClick = {
                    if (!isActive) onSwitchVersion(version.versionId)
                }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isActive) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            if (renamingVersionId == version.versionId) {
                                OutlinedTextField(
                                    value = renameText,
                                    onValueChange = { renameText = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Version name") }
                                )
                            } else {
                                Text(
                                    text = version.displayName.ifEmpty { "Version" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (renamingVersionId == version.versionId) {
                            IconButton(
                                onClick = {
                                    onRenameVersion(version.versionId, renameText)
                                    renamingVersionId = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { renamingVersionId = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    renameText = version.displayName
                                    renamingVersionId = version.versionId
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(
                                onClick = { deleteConfirmId = version.versionId },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${version.transactions.size} edits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isActive && version.transactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Recent edits:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        version.transactions.takeLast(5).reversed().forEach { tx ->
                            Text(
                                text = "${tx.operatorType.name} · ${
                                    DateTimeFormatter.ofPattern("HH:mm:ss")
                                        .withZone(java.time.ZoneId.systemDefault())
                                        .format(tx.timestamp)
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Create version dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Version") },
            text = {
                OutlinedTextField(
                    value = newVersionName,
                    onValueChange = { newVersionName = it },
                    label = { Text("Version name") },
                    placeholder = { Text("Version ${orderedVersions.size + 1}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCreateVersion(newVersionName.ifEmpty { "Version ${orderedVersions.size + 1}" })
                    showCreateDialog = false
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation
    if (deleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete Version") },
            text = { Text("Are you sure you want to delete this version? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVersion(deleteConfirmId!!)
                        deleteConfirmId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}