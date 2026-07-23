package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.EditHistory
import com.alcedo.studio.data.model.EditTransaction
import com.alcedo.studio.data.model.OperatorType
import com.alcedo.studio.data.model.Version
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing
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

    // P2-6 撤销/重做可视化：获取当前工作版本的事务列表与游标位置
    val workingVersion by viewModel.workingVersion
    val transactions = workingVersion.transactions
    val currentIndex = workingVersion.cursor

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
    ) {
        // ── Header with Undo/Redo ──────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${stringRes { historyVersions }} (${orderedVersions.size})",
                        style = MaterialTheme.typography.titleSmall,
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

                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                Button(
                    onClick = {
                        newVersionName = ""
                        showCreateDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AlcedoIconSize.sm))
                    Spacer(modifier = Modifier.width(AlcedoSpacing.sm))
                    Text(stringRes { historyNewVersion })
                }
            }
        }

        // ── P2-6 可视化事务列表（点击跳转、高亮当前位置） ────────
        if (transactions.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        text = "${stringRes { historyRecentEdits }} (${transactions.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AlcedoSpacing.xs)
                    )

                    Column(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                    ) {
                        transactions.forEachIndexed { index, tx ->
                            val isCurrent = index < currentIndex
                            val isCursor = index == currentIndex - 1
                            HistoryItem(
                                transaction = tx,
                                isCurrent = isCurrent,
                                isCursorAt = isCursor,
                                onClick = {
                                    HapticFeedback.click(view)
                                    viewModel.jumpToHistoryStep(index + 1)
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── Version List ───────────────────────────────────────────
        orderedVersions.forEach { version ->
            val isActive = version.versionId == activeVersionId
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
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
                                    modifier = Modifier.size(AlcedoIconSize.sm)
                                )
                                Spacer(modifier = Modifier.width(AlcedoSpacing.sm))
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
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringRes { confirm },
                                    modifier = Modifier.size(AlcedoIconSize.sm)
                                )
                            }
                            IconButton(
                                onClick = { renamingVersionId = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringRes { historyCancel },
                                    modifier = Modifier.size(AlcedoIconSize.sm)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    renameText = version.displayName
                                    renamingVersionId = version.versionId
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringRes { historyRename },
                                    modifier = Modifier.size(AlcedoIconSize.sm)
                                )
                            }
                            IconButton(
                                onClick = { deleteConfirmId = version.versionId },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringRes { historyDelete },
                                    modifier = Modifier.size(AlcedoIconSize.sm),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))

                    Text(
                        text = stringRes { inspectorEditsCount }.format(version.transactions.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isActive && version.transactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(AlcedoSpacing.sm))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
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
                                modifier = Modifier.padding(start = AlcedoSpacing.sm, top = AlcedoSpacing.xs)
                            )
                        }
                    }

                    // Switch version button for non-active versions
                    if (!isActive) {
                        Spacer(modifier = Modifier.height(AlcedoSpacing.sm))
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

// ═══════════════════════════════════════════════════════════════════
// P2-6 历史步骤可视化组件
// ═══════════════════════════════════════════════════════════════════

/** 根据 [OperatorType] 返回对应的图标。 */
@Composable
private fun getOperatorIcon(type: OperatorType) = when (type) {
    OperatorType.EXPOSURE -> Icons.Default.Brightness6
    OperatorType.CONTRAST -> Icons.Default.Contrast
    OperatorType.SATURATION -> Icons.Default.Palette
    OperatorType.VIBRANCE -> Icons.AutoMirrored.Filled.ArrowForward
    OperatorType.HIGHLIGHTS, OperatorType.SHADOWS -> Icons.Default.Tune
    OperatorType.WHITE_BALANCE -> Icons.Default.WbSunny
    OperatorType.TONE_CURVE -> Icons.Default.ShowChart
    OperatorType.HSL -> Icons.Default.Colorize
    OperatorType.COLOR_WHEEL -> Icons.Default.Circle
    OperatorType.GEOMETRY, OperatorType.CROP -> Icons.Default.CropRotate
    OperatorType.FILM_GRAIN -> Icons.Default.Grain
    OperatorType.HALATION -> Icons.Default.FlashOn
    OperatorType.LUT -> Icons.Default.PhotoLibrary
    OperatorType.DISPLAY_TRANSFORM -> Icons.Default.SettingsBrightness
    OperatorType.RAW_DECODE -> Icons.Default.CameraAlt
    OperatorType.SHARPEN -> Icons.Default.AutoFixHigh
    OperatorType.CLARITY -> Icons.Default.FilterCenterFocus
    OperatorType.TINT -> Icons.Default.WaterDrop
    OperatorType.TONE_REGION -> Icons.Default.Equalizer
    OperatorType.PRESET -> Icons.Default.Bookmark
    OperatorType.DENOISE -> Icons.Default.BlurOn
    OperatorType.VIGNETTE -> Icons.Default.Vignette
    OperatorType.PERSPECTIVE -> Icons.Default.Transform
    OperatorType.LENS_CORRECTION -> Icons.Default.CameraAlt
}

/**
 * 单个历史步骤项。
 * [isCurrent]: 该步骤是否在已应用范围内。
 * [isCursorAt]: 是否为游标指向的最后一步（高亮显示）。
 */
@Composable
private fun HistoryItem(
    transaction: EditTransaction,
    isCurrent: Boolean,
    isCursorAt: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isCursorAt -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        isCurrent -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else -> Color.Unspecified
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCursorAt) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AlcedoSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 操作类型图标
            Icon(
                imageVector = getOperatorIcon(transaction.operatorType),
                contentDescription = null,
                tint = if (isCurrent || isCursorAt) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AlcedoIconSize.md)
            )

            Spacer(modifier = Modifier.width(AlcedoSpacing.sm))

            // 操作名称
            Text(
                text = buildTransactionLabel(transaction),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent || isCursorAt) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 时间
            Text(
                text = DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(transaction.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isCursorAt) {
                Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

/** 从 [EditTransaction] 中提取可读的操作标签。 */
private fun buildTransactionLabel(tx: EditTransaction): String {
    return when (tx.operatorType) {
        OperatorType.PRESET -> {
            val desc = extractDescriptionKey(tx.paramsAfter)?.takeIf { it.isNotBlank() } ?: "Preset"
            "Preset · ${desc.removePrefix("applyPreset:")}"
        }
        else -> tx.operatorType.name.replaceFirstChar { it.uppercase() }.let {
            val key = extractDescriptionKey(tx.paramsAfter)
            key?.takeIf { k -> k.isNotBlank() } ?: it
        }
    }
}

private fun extractDescriptionKey(json: kotlinx.serialization.json.JsonObject): String? =
    json.entries.firstOrNull()?.key
