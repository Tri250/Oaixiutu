package com.alcedo.studio.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.domain.service.AdjustmentFilter
import com.alcedo.studio.domain.service.PresetWithThumbnail
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.viewmodel.AlbumViewModel

/**
 * RapidRAW-inspired batch-edit bottom sheet.
 *
 * Appears when the user has multiple images selected and taps the "Batch Edit"
 * action. Provides copy / paste / selective paste / apply preset / reset
 * operations, real-time progress, and snackbar-style feedback wired to
 * [AlbumViewModel.batchEditMessage].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchEditPanel(
    viewModel: AlbumViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedImages by viewModel.selectedImages.collectAsStateWithLifecycle()
    val clipboardParams by viewModel.clipboardParams.collectAsStateWithLifecycle()
    val clipboardSourceId by viewModel.clipboardSourceId.collectAsStateWithLifecycle()
    val progress by viewModel.batchEditProgress.collectAsStateWithLifecycle()
    val message by viewModel.batchEditMessage.collectAsStateWithLifecycle()
    val presets by viewModel.batchPresets.collectAsStateWithLifecycle()

    var showSelectivePasteDialog by remember { mutableStateOf(false) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // Surface snackbar-style messages via a transient AlertDialog-like Text.
    // AlbumScreen already collects batchEditMessage for the actual Snackbar,
    // so we just clear it after a short delay here to avoid stale state.
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearBatchEditMessage()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        BatchEditPanelContent(
            selectedCount = selectedImages.size,
            hasClipboard = clipboardParams != null,
            clipboardSourceId = clipboardSourceId,
            progressActive = progress.isActive,
            progressCompleted = progress.completed,
            progressTotal = progress.total,
            progressOperation = progress.operation,
            message = message,
            onCopy = { viewModel.copyAdjustments() },
            onPaste = { viewModel.pasteAdjustments() },
            onSelectivePaste = { showSelectivePasteDialog = true },
            onApplyPreset = {
                viewModel.loadBatchPresets()
                showPresetPicker = true
            },
            onReset = { showResetConfirm = true }
        )
    }

    if (showSelectivePasteDialog) {
        SelectivePasteDialog(
            initialFilter = AdjustmentFilter(),
            onDismiss = { showSelectivePasteDialog = false },
            onApply = { filter ->
                viewModel.pastePartialAdjustments(filter)
                showSelectivePasteDialog = false
            }
        )
    }

    if (showPresetPicker) {
        PresetPickerDialog(
            presets = presets,
            onDismiss = { showPresetPicker = false },
            onPick = { preset ->
                viewModel.applyPresetBatch(preset.id)
                showPresetPicker = false
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringRes { batchConfirmResetTitle }) },
            text = {
                Text(stringRes { batchConfirmResetMessage }.format(selectedImages.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetBatchAdjustments()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringRes { reset }) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringRes { cancel })
                }
            }
        )
    }
}

@Composable
private fun BatchEditPanelContent(
    selectedCount: Int,
    hasClipboard: Boolean,
    clipboardSourceId: Long?,
    progressActive: Boolean,
    progressCompleted: Int,
    progressTotal: Int,
    progressOperation: String,
    message: String?,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectivePaste: () -> Unit,
    onApplyPreset: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringRes { batchPanelTitle }.format(selectedCount),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        // Clipboard status
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = if (hasClipboard) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (hasClipboard) {
                        clipboardSourceId?.let {
                            stringRes { batchClipboardFrom }.format(it)
                        } ?: stringRes { batchPasteAdjustments }
                    } else {
                        stringRes { batchClipboardEmpty }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasClipboard) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Progress bar
        AnimatedVisibility(visible = progressActive, enter = fadeIn(), exit = fadeOut()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (progressOperation.isNotEmpty()) progressOperation
                        else stringRes { batchProcessing },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringRes { batchProcessed }.format(progressCompleted, progressTotal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        if (progressTotal <= 0) 0f
                        else progressCompleted.toFloat() / progressTotal.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Inline message (acts as a transient snackbar fallback)
        AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
            message?.let {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Action buttons
        BatchActionButton(
            icon = Icons.Default.ContentCopy,
            label = stringRes { batchCopyAdjustments },
            enabled = selectedCount >= 1 && !progressActive,
            onClick = onCopy
        )
        BatchActionButton(
            icon = Icons.Default.ContentPaste,
            label = stringRes { batchPasteAdjustments },
            enabled = hasClipboard && selectedCount >= 1 && !progressActive,
            onClick = onPaste
        )
        BatchActionButton(
            icon = Icons.Default.FilterList,
            label = stringRes { batchSelectivePaste },
            enabled = hasClipboard && selectedCount >= 1 && !progressActive,
            onClick = onSelectivePaste
        )
        BatchActionButton(
            icon = Icons.Default.AutoFixHigh,
            label = stringRes { batchApplyPreset },
            enabled = selectedCount >= 1 && !progressActive,
            onClick = onApplyPreset
        )
        BatchActionButton(
            icon = Icons.Default.Restore,
            label = stringRes { batchResetAdjustments },
            enabled = selectedCount >= 1 && !progressActive,
            onClick = onReset,
            isDestructive = true
        )
    }
}

@Composable
private fun BatchActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val containerColor = if (isDestructive) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer

    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Dialog with one checkbox per [AdjustmentFilter] category. Returns a fresh
 * filter constructed from the user's selections when "Apply" is tapped.
 */
@Composable
private fun SelectivePasteDialog(
    initialFilter: AdjustmentFilter,
    onDismiss: () -> Unit,
    onApply: (AdjustmentFilter) -> Unit
) {
    var copyBasic by remember { mutableStateOf(initialFilter.copyBasic) }
    var copyWhiteBalance by remember { mutableStateOf(initialFilter.copyWhiteBalance) }
    var copyColor by remember { mutableStateOf(initialFilter.copyColor) }
    var copyToneCurve by remember { mutableStateOf(initialFilter.copyToneCurve) }
    var copyEffects by remember { mutableStateOf(initialFilter.copyEffects) }
    var copyGeometry by remember { mutableStateOf(initialFilter.copyGeometry) }
    var copyLut by remember { mutableStateOf(initialFilter.copyLut) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes { batchSelectivePaste }) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterCheckboxRow(stringRes { batchFilterBasic }, copyBasic) { copyBasic = it }
                FilterCheckboxRow(stringRes { batchFilterWhiteBalance }, copyWhiteBalance) { copyWhiteBalance = it }
                FilterCheckboxRow(stringRes { batchFilterColor }, copyColor) { copyColor = it }
                FilterCheckboxRow(stringRes { batchFilterToneCurve }, copyToneCurve) { copyToneCurve = it }
                FilterCheckboxRow(stringRes { batchFilterEffects }, copyEffects) { copyEffects = it }
                FilterCheckboxRow(stringRes { batchFilterGeometry }, copyGeometry) { copyGeometry = it }
                FilterCheckboxRow(stringRes { batchFilterLut }, copyLut) { copyLut = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    AdjustmentFilter(
                        copyBasic = copyBasic,
                        copyWhiteBalance = copyWhiteBalance,
                        copyColor = copyColor,
                        copyToneCurve = copyToneCurve,
                        copyEffects = copyEffects,
                        copyGeometry = copyGeometry,
                        copyLut = copyLut
                    )
                )
            }) { Text(stringRes { apply }) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}

@Composable
private fun FilterCheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Lightweight preset picker — a scrollable list of preset names. Tapping a
 * row triggers [onPick]. Real thumbnails are not rendered here to keep the
 * batch picker fast even with hundreds of presets.
 */
@Composable
private fun PresetPickerDialog(
    presets: List<PresetWithThumbnail>,
    onDismiss: () -> Unit,
    onPick: (PresetWithThumbnail) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes { batchPickPreset }) },
        text = {
            if (presets.isEmpty()) {
                Text(
                    stringRes { batchProcessing },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    items(presets, key = { it.id }) { preset ->
                        TextButton(
                            onClick = { onPick(preset) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    preset.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}
