package com.alcedo.studio.ui.album

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.ExportConfig
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.PipelinePreset
import com.alcedo.studio.data.model.SortField
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.BackgroundTaskBar
import com.alcedo.studio.ui.common.EmptyState
import com.alcedo.studio.ui.common.ErrorDialog
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens
import com.alcedo.studio.storage.PhotoPickerHelper
import com.alcedo.studio.storage.rememberImagePicker
import androidx.compose.ui.platform.LocalContext

/**
 * Full album browser. Composes a top app bar with search, sort and filter
 * chips, a zoom slider; a left [CollectionsPanel] sidebar; a center grid/list
 * of thumbnails; a right inspector panel; and a bottom [BackgroundTaskBar].
 *
 * State is owned by [AlbumViewModel] and collected with
 * [collectAsStateWithLifecycle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    onOpenImage: (String) -> Unit,
    onExportSelected: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tasks by viewModel.backgroundTasks.collectAsStateWithLifecycle()

    // Local UI state.
    var isGridView by remember { mutableStateOf(true) }
    var columnCount by remember { mutableStateOf(6) }
    var searchInput by remember { mutableStateOf("") }
    var sortExpanded by remember { mutableStateOf(false) }
    var contextMenuImage by remember { mutableStateOf<ImageItem?>(null) }
    var contextMenuOffset by remember { mutableStateOf(androidx.compose.ui.unit.DpOffset.Zero) }
    var showBatchPanel by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showInspector by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(true) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var exportConfig by remember { mutableStateOf(ExportConfig()) }

    // Back presses first dismiss the context menu / batch panel / selection,
    // then fall through to the system so the host navigation works.
    BackHandler(enabled = contextMenuImage != null || showBatchPanel || state.selection.isNotEmpty()) {
        when {
            contextMenuImage != null -> contextMenuImage = null
            showBatchPanel -> showBatchPanel = false
            state.selection.isNotEmpty() -> viewModel.clearSelection()
        }
    }

    // Image picker for import.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.import(uris)
    }

    // Photo picker (AndroidX Photo Picker, falls back to GET_CONTENT)
    val photoPicker = rememberImagePicker(maxItems = 50) { uris ->
        if (uris.isNotEmpty()) viewModel.import(uris)
    }
    val isPhotoPickerAvailable = remember { PhotoPickerHelper.isPhotoPickerAvailable(LocalContext.current) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = {
                            searchInput = it
                            viewModel.search(it)
                        },
                        placeholder = { Text(s.searchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                actions = {
                    // Sort menu
                    Box {
                        IconButton(onClick = { sortExpanded = true }) {
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = s.sort, tint = AlcedoColors.TextSecondary)
                        }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            SortField.entries.forEach { field ->
                                DropdownMenuItem(
                                    text = { Text(field.name.replace('_', ' ')) },
                                    onClick = {
                                        viewModel.setSort(
                                            com.alcedo.studio.data.model.SortDescriptor(
                                                field,
                                                ascending = state.sort.field != field || !state.sort.ascending,
                                            ),
                                        )
                                        sortExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                            contentDescription = if (isGridView) s.listView else s.gridView,
                        )
                    }
                    IconButton(onClick = { showCollections = !showCollections }) {
                        Icon(Icons.Outlined.Menu, contentDescription = s.collections, tint = AlcedoColors.TextSecondary)
                    }
                    if (state.selection.isNotEmpty()) {
                        IconButton(onClick = { showBatchPanel = !showBatchPanel }) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = s.batchEdit)
                        }
                        IconButton(onClick = { showExportDialog = true }) {
                            BadgedBox(badge = {
                                Badge { Text(state.selection.size.toString()) }
                            }) {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = "${s.exportTitle} (${state.selection.size})",
                                    tint = AlcedoColors.AccentBlue,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AlcedoColors.Charcoal,
                    titleContentColor = AlcedoColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left: collections sidebar
                if (showCollections) {
                    CollectionsPanel(
                        folders = state.folders,
                        selectedPath = state.folderPath,
                        modifier = Modifier.width(220.dp).fillMaxHeight(),
                        onSelectFolder = { viewModel.setFolder(it) },
                        onCreateFolder = { showCreateFolderDialog = true },
                        onImport = {
                            if (isPhotoPickerAvailable) {
                                photoPicker.launch(PhotoPickerHelper.imageOnly())
                            } else {
                                importLauncher.launch(arrayOf("image/*"))
                            }
                        },
                    )
                }

                // Center: thumbnails + zoom slider
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Filter chips + zoom slider row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AlcedoColors.SurfaceRaised)
                            .padding(horizontal = DesignTokens.spacingMd, vertical = DesignTokens.spacingXs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                    ) {
                        FilterChip(
                            selected = state.filter.flags.isNotEmpty(),
                            onClick = { viewModel.clearFilter() },
                            label = { Text(if (state.filter.isEmpty) "All" else "Filtered") },
                        )
                        if (state.isImporting || state.isCulling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AlcedoColors.AccentBlue,
                            )
                        }
                        Box(modifier = Modifier.weight(1f))
                        Text(s.thumbnailSize, style = MaterialTheme.typography.labelSmall, color = AlcedoColors.TextTertiary)
                        Slider(
                            value = columnCount.toFloat(),
                            onValueChange = { columnCount = it.toInt() },
                            valueRange = 2f..14f,
                            modifier = Modifier.width(120.dp),
                        )
                    }

                    // Grid / list
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.isImporting && state.images.isEmpty() -> {
                                ThumbnailGridSkeleton(columnCount)
                            }
                            state.images.isEmpty() && state.searchQuery.isBlank() -> {
                                EmptyState(
                                    title = s.emptyAlbumTitle,
                                    subtitle = s.emptyAlbumSubtitle,
                                    actionText = s.import,
                                    onAction = {
                                        if (isPhotoPickerAvailable) {
                                            photoPicker.launch(PhotoPickerHelper.imageOnly())
                                        } else {
                                            importLauncher.launch(arrayOf("image/*"))
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            state.images.isEmpty() -> {
                                EmptyState(
                                    title = s.noSearchResults,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            else -> {
                                if (isGridView) {
                                    ThumbnailGridView(
                                        images = state.images,
                                        selection = state.selection,
                                        columnCount = columnCount,
                                        onOpen = { onOpenImage(it.id) },
                                        onToggleSelection = { viewModel.toggleSelection(it) },
                                        onLongPress = { img, off ->
                                            contextMenuImage = img
                                            contextMenuOffset = off
                                        },
                                    )
                                } else {
                                    ThumbnailListView(
                                        images = state.images,
                                        selection = state.selection,
                                        onOpen = { onOpenImage(it.id) },
                                        onToggleSelection = { viewModel.toggleSelection(it) },
                                        onLongPress = { img, off ->
                                            contextMenuImage = img
                                            contextMenuOffset = off
                                        },
                                    )
                                }
                            }
                        }

                        // Context menu anchored over the grid
                        ImageContextMenu(
                            expanded = contextMenuImage != null,
                            image = contextMenuImage,
                            offset = contextMenuOffset,
                            onDismiss = { contextMenuImage = null },
                            onCopyAdjustments = {
                                contextMenuImage?.let { viewModel.copyAdjustments(it.id) }
                                contextMenuImage = null
                            },
                            onPasteAdjustments = {
                                contextMenuImage?.let { viewModel.pasteAdjustments(it.id) }
                                contextMenuImage = null
                            },
                            onRate = { rating ->
                                contextMenuImage?.let { viewModel.setRating(it.id, rating) }
                                contextMenuImage = null
                            },
                            onSetFlag = { flag ->
                                contextMenuImage?.let { viewModel.setFlag(it.id, flag) }
                                contextMenuImage = null
                            },
                            onSetColorLabel = { label ->
                                contextMenuImage?.let { viewModel.setColorLabel(it.id, label) }
                                contextMenuImage = null
                            },
                            onAddToCollection = {
                                contextMenuImage?.let { viewModel.addToCollection(it.id) }
                                contextMenuImage = null
                            },
                            onDelete = {
                                contextMenuImage?.let { viewModel.delete(it.id) }
                                contextMenuImage = null
                            },
                        )
                    }
                }

                // Right: inspector / stats / batch panel
                if (showInspector || showBatchPanel || state.selection.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .width(DesignTokens.inspectorWidth)
                            .fillMaxHeight()
                            .background(AlcedoColors.Graphite)
                            .padding(DesignTokens.spacingSm),
                    ) {
                        val ratedCount = state.images.count { it.rating > 0 }
                        val taggedCount = state.images.count { it.aiTags.isNotEmpty() }
                        StatsView(
                            stats = state.stats,
                            cameras = state.cameras,
                            lenses = state.lenses,
                            ratedCount = ratedCount,
                            taggedCount = taggedCount,
                        )
                        if (showBatchPanel || state.selection.isNotEmpty()) {
                            BatchEditPanel(
                                selectedCount = state.selection.size,
                                onApplyPreset = { showPresetPicker = true },
                                onSyncFromFirst = { viewModel.cullSelection() },
                                onClearAdjustments = { viewModel.clearSelection() },
                            )
                        }
                    }
                }
            }

            // Bottom: background task bar
            BackgroundTaskBar(
                tasks = tasks,
                modifier = Modifier.align(Alignment.BottomCenter),
                onCancel = { taskId -> viewModel.cancelTask(taskId) },
            )

            // Export dialog
            if (showExportDialog) {
                AlbumExportDialog(
                    config = exportConfig,
                    count = state.selection.size.coerceAtLeast(1),
                    onConfigChange = { exportConfig = it },
                    onExport = {
                        val ids = state.selection.toList()
                        onExportSelected(ids)
                        showExportDialog = false
                    },
                    onDismiss = { showExportDialog = false },
                )
            }
        }
    }

    // Error surfacing
    state.error?.let { err ->
        ErrorDialog(
            title = s.error,
            message = err,
            onDismiss = { viewModel.dismissError() },
            retryText = s.retry,
            onRetry = { viewModel.refreshStats(); viewModel.dismissError() },
        )
    }

    // Create-folder dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(s.createFolder) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    enabled = !state.isCreatingFolder,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createFolder(folderName)
                        showCreateFolderDialog = false
                    },
                    enabled = folderName.isNotBlank() && !state.isCreatingFolder,
                ) {
                    Text(s.create, color = AlcedoColors.AccentBlue)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateFolderDialog = false },
                    enabled = !state.isCreatingFolder,
                ) {
                    Text(s.cancel, color = AlcedoColors.TextSecondary)
                }
            },
        )
    }

    // Preset picker for batch apply
    if (showPresetPicker) {
        PresetPickerDialog(
            presets = state.presets,
            onSelect = { preset ->
                viewModel.applyPresetToSelection(preset.id)
                showPresetPicker = false
            },
            onDismiss = { showPresetPicker = false },
        )
    }

    // Info message surfacing
    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            title = { Text(s.done) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMessage() }) {
                    Text(s.close, color = AlcedoColors.AccentBlue)
                }
            },
        )
    }
}

@Composable
private fun PresetPickerDialog(
    presets: List<PipelinePreset>,
    onSelect: (PipelinePreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = Strings.res
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.presets) },
        text = {
            if (presets.isEmpty()) {
                Text(
                    text = "No presets available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlcedoColors.TextTertiary,
                )
            } else {
                LazyColumn {
                    items(presets, key = { it.id }) { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(preset) }
                                .padding(vertical = DesignTokens.spacingSm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AlcedoColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = preset.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = AlcedoColors.TextTertiary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel, color = AlcedoColors.TextSecondary)
            }
        },
    )
}
