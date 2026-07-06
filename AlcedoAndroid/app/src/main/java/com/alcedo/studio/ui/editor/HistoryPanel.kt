package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.EditHistory
import com.alcedo.studio.data.model.Version
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.EditorViewModel
import java.time.format.DateTimeFormatter

@Composable
fun HistoryPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current

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
        // ── Header with Undo/Redo ──────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${stringRes { historyVersions }} (${orderedVersions.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row {
                        IconButton(
                            onClick = {
                                HapticFeedback.click(view)
                                viewModel.undo()
                            },
                            enabled = canUndo
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = stringRes { historyUndo },
                                tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(
                            onClick = {
                                HapticFeedback.click(view)
                                viewModel.redo()
                            },
                            enabled = canRedo
                        ) {
                            Icon(
                                Icons.Default.Redo,
                                contentDescription = stringRes { historyRedo },
                                tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        IconButton(onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.cloneHistory()
                        }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringRes { editorCloneHistory },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        newVersionName = ""
                        showCreateDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringRes { historyNewVersion })
                }
            }
        }

        // ── Version List ───────────────────────────────────────────
        orderedVersions.forEach { version ->
            val isActive = version.versionId == activeVersionId
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
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
                                    contentDescription = stringRes { active },
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
                                    label = { Text(stringRes { historyVersionName }) }
                                )
                            } else {
                                Text(
                                    text = version.displayName.ifEmpty { stringRes { historyVersions } },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (renamingVersionId == version.versionId) {
                            IconButton(
                                onClick = {
                                    viewModel.renameVersion(version.versionId, renameText)
                                    renamingVersionId = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringRes { confirm },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { renamingVersionId = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringRes { historyCancel },
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
                                    contentDescription = stringRes { historyRename },
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(
                                onClick = { deleteConfirmId = version.versionId },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringRes { historyDelete },
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
                            stringRes { historyRecentEdits },
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

                    // Switch version button for non-active versions
                    if (!isActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.switchVersion(version.versionId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringRes { historySwitchToVersion })
                        }
                    }
                }
            }
        }
    }

    // ── Create version dialog ──────────────────────────────────────
    if (showCreateDialog) {
        val defaultVersionName = "${stringRes { historyVersions }} ${orderedVersions.size + 1}"
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringRes { historyNewVersion }) },
            text = {
                OutlinedTextField(
                    value = newVersionName,
                    onValueChange = { newVersionName = it },
                    label = { Text(stringRes { historyVersionName }) },
                    placeholder = { Text(defaultVersionName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    HapticFeedback.success(context)
                    viewModel.createVersion(newVersionName.ifEmpty { defaultVersionName })
                    showCreateDialog = false
                }) {
                    Text(stringRes { historyCreate })
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringRes { historyCancel })
                }
            }
        )
    }

    // ── Delete confirmation ────────────────────────────────────────
    if (deleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text(stringRes { historyDeleteVersionTitle }) },
            text = { Text(stringRes { historyDeleteVersionConfirm }) },
            confirmButton = {
                Button(
                    onClick = {
                        HapticFeedback.heavyClick(view)
                        viewModel.deleteVersion(deleteConfirmId!!)
                        deleteConfirmId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringRes { historyDelete })
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) {
                    Text(stringRes { historyCancel })
                }
            }
        )
    }
}
