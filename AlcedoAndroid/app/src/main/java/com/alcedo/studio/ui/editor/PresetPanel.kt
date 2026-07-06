package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.domain.service.PresetService
import com.alcedo.studio.domain.service.PresetWithThumbnail
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.viewmodel.EditorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Filter chips shown above the preset grid.
 */
enum class PresetFilter(val labelKey: StringResources.() -> String) {
    ALL({ presetCategoryAll }),
    BUILTIN({ presetCategoryBuiltin }),
    CUSTOM({ presetCategoryCustom }),
    FILM({ presetCategoryFilm }),
    PORTRAIT({ presetCategoryPortrait }),
    LANDSCAPE({ presetCategoryLandscape }),
    BW({ presetCategoryBW })
}

private val PRESET_CATEGORIES = listOf(
    PresetService.CATEGORY_FILM,
    PresetService.CATEGORY_PORTRAIT,
    PresetService.CATEGORY_LANDSCAPE,
    PresetService.CATEGORY_BW,
    PresetService.CATEGORY_GENERAL
)

private fun categoryColor(category: String): Color = when (category) {
    PresetService.CATEGORY_FILM -> Color(0xFFE0A040)
    PresetService.CATEGORY_PORTRAIT -> Color(0xFFE07A9A)
    PresetService.CATEGORY_LANDSCAPE -> Color(0xFF5CB670)
    PresetService.CATEGORY_BW -> Color(0xFF9E9E9E)
    else -> Color(0xFF5C9BB6)
}

/**
 * RapidRAW-inspired preset management panel.
 *
 * Shows a 3-column grid of presets with REAL pipeline-rendered thumbnails,
 * a search bar, category filter chips, long-press actions (Apply / Edit /
 * Export / Delete), a "Create from Current" action, import/export, and a
 * brief before/after preview overlay on tap.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PresetPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val presetService = remember { viewModel.presetService }

    val presets by presetService.getAllPresets()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val currentPreview by viewModel.previewBitmap.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PresetFilter.ALL) }
    var contextMenuPreset by remember { mutableStateOf<PresetWithThumbnail?>(null) }
    var previewOverlayPreset by remember { mutableStateOf<PresetWithThumbnail?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PresetWithThumbnail?>(null) }
    var deleteTarget by remember { mutableStateOf<PresetWithThumbnail?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Precompute string resources in the @Composable scope so they can be
    // referenced inside coroutines / try-catch blocks (composables cannot be
    // invoked from those contexts).
    val errorMsg = stringRes { error }
    val importedMsg = stringRes { presetImported }
    val exportedFmt = stringRes { presetExported }
    val createdMsg = stringRes { presetCreated }
    val editedMsg = stringRes { presetEdit }
    val deletedMsg = stringRes { presetDeleted }

    // Show snackbar messages
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    // Import launcher — picks a JSON file and imports it as a preset
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                try {
                    val tempFile = File(context.cacheDir, "import_preset_${System.currentTimeMillis()}.json")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    presetService.importPreset(tempFile.absolutePath)
                    snackbarMessage = importedMsg
                    tempFile.delete()
                } catch (_: Throwable) {
                    snackbarMessage = errorMsg
                } finally {
                    isBusy = false
                }
            }
        }
    }

    // Auto-dismiss the before/after preview overlay
    LaunchedEffect(previewOverlayPreset) {
        if (previewOverlayPreset != null) {
            delay(1200)
            previewOverlayPreset = null
        }
    }

    val filteredPresets = remember(presets, searchQuery, selectedFilter) {
        presets.filter { p ->
            val matchesSearch = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                PresetFilter.ALL -> true
                PresetFilter.BUILTIN -> p.isBuiltIn
                PresetFilter.CUSTOM -> !p.isBuiltIn
                PresetFilter.FILM -> p.category == PresetService.CATEGORY_FILM
                PresetFilter.PORTRAIT -> p.category == PresetService.CATEGORY_PORTRAIT
                PresetFilter.LANDSCAPE -> p.category == PresetService.CATEGORY_LANDSCAPE
                PresetFilter.BW -> p.category == PresetService.CATEGORY_BW
            }
            matchesSearch && matchesFilter
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Manual column-of-rows grid (3 columns) — avoids nesting LazyVerticalGrid
        // inside the editor's vertically scrolling panel container.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Top action bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringRes { presetCreate },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringRes { presetImport },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            try {
                                val dir = File(context.getExternalFilesDir(null), "presets").apply { mkdirs() }
                                val count = presetService.exportAllPresets(dir.absolutePath)
                                snackbarMessage = "${exportedFmt.format(dir.absolutePath)} ($count)"
                            } catch (_: Throwable) {
                                snackbarMessage = errorMsg
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringRes { presetExportAll },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }

            // ── Search bar ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringRes { presetSearch }, style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )

            // ── Category filter chips ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                stringRes(filter.labelKey),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // ── Preset grid (3 columns, manual rows) ──
            if (filteredPresets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringRes { presetTitle },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                val rows = filteredPresets.chunked(3)
                rows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            PresetCard(
                                preset = preset,
                                modifier = Modifier.weight(1f),
                                onTap = {
                                    HapticFeedback.click(view)
                                    previewOverlayPreset = preset
                                },
                                onLongPress = {
                                    HapticFeedback.heavyClick(view)
                                    contextMenuPreset = preset
                                }
                            )
                        }
                        // Pad the last row so cards keep equal width
                        repeat(3 - rowPresets.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── Before/after preview overlay ──
        if (previewOverlayPreset != null) {
            BeforeAfterPreviewOverlay(
                preset = previewOverlayPreset!!,
                presetService = presetService,
                onDismiss = { previewOverlayPreset = null },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Busy scrim ──
        if (isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ── Context menu (long-press) ──
    if (contextMenuPreset != null) {
        PresetContextMenu(
            preset = contextMenuPreset!!,
            onDismiss = { contextMenuPreset = null },
            onApply = {
                val target = contextMenuPreset!!
                contextMenuPreset = null
                scope.launch {
                    isBusy = true
                    try {
                        val params = presetService.applyPreset(target.id)
                        viewModel.applyPresetParams(params)
                        snackbarMessage = target.name
                    } catch (_: Throwable) {
                        snackbarMessage = errorMsg
                    } finally {
                        isBusy = false
                    }
                }
            },
            onEdit = {
                editTarget = contextMenuPreset
                contextMenuPreset = null
            },
            onExport = {
                val target = contextMenuPreset!!
                contextMenuPreset = null
                scope.launch {
                    isBusy = true
                    try {
                        val dir = File(context.getExternalFilesDir(null), "presets").apply { mkdirs() }
                        val safeName = target.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        val out = File(dir, "$safeName.json")
                        val ok = presetService.exportPreset(target.id, out.absolutePath)
                        snackbarMessage = if (ok) exportedFmt.format(out.absolutePath)
                        else errorMsg
                    } catch (_: Throwable) {
                        snackbarMessage = errorMsg
                    } finally {
                        isBusy = false
                    }
                }
            },
            onDelete = {
                deleteTarget = contextMenuPreset
                contextMenuPreset = null
            }
        )
    }

    // ── Create preset dialog ──
    if (showCreateDialog) {
        PresetNameCategoryDialog(
            title = stringRes { presetCreate },
            initialName = "",
            initialCategory = PresetService.CATEGORY_GENERAL,
            confirmLabel = stringRes { presetCreate },
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, category, _ ->
                showCreateDialog = false
                scope.launch {
                    isBusy = true
                    try {
                        presetService.createPreset(
                            name = name,
                            category = category,
                            params = viewModel.params.value,
                            thumbnailBitmap = currentPreview
                        )
                        snackbarMessage = createdMsg
                    } catch (_: Throwable) {
                        snackbarMessage = errorMsg
                    } finally {
                        isBusy = false
                    }
                }
            }
        )
    }

    // ── Edit preset dialog ──
    if (editTarget != null) {
        val target = editTarget!!
        PresetNameCategoryDialog(
            title = stringRes { presetEdit },
            initialName = target.name,
            initialCategory = target.category,
            confirmLabel = stringRes { presetEdit },
            allowUpdateParams = !target.isBuiltIn,
            onDismiss = { editTarget = null },
            onConfirm = { name, category, updateParams ->
                val t = target
                editTarget = null
                scope.launch {
                    isBusy = true
                    try {
                        val params = if (updateParams) viewModel.params.value else t.params
                        presetService.updatePreset(t.id, name, category, params)
                        snackbarMessage = editedMsg
                    } catch (_: Throwable) {
                        snackbarMessage = errorMsg
                    } finally {
                        isBusy = false
                    }
                }
            }
        )
    }

    // ── Delete confirmation ──
    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringRes { presetDelete }) },
            text = { Text(target.name) },
            confirmButton = {
                Button(
                    onClick = {
                        val t = target
                        deleteTarget = null
                        scope.launch {
                            isBusy = true
                            try {
                                presetService.deletePreset(t.id)
                                snackbarMessage = deletedMsg
                            } catch (_: Throwable) {
                                snackbarMessage = errorMsg
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringRes { presetDelete }) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringRes { cancel }) }
            }
        )
    }
}

// ================================================================
// Preset card
// ================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    preset: PresetWithThumbnail,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = modifier
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thumbnail (REAL pipeline-rendered bitmap)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101010)),
                contentAlignment = Alignment.Center
            ) {
                val thumb = preset.thumbnail
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = preset.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                // Built-in badge
                if (preset.isBuiltIn) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .background(
                                Color.Black.copy(alpha = 0.55f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            stringRes { presetCategoryBuiltin },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Preset name
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            // Category badge
            val catColor = categoryColor(preset.category)
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(catColor.copy(alpha = 0.18f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = preset.category.ifBlank { stringRes { presetCategoryCustom } },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                    color = catColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ================================================================
// Context menu (Apply / Edit / Export / Delete)
// ================================================================

@Composable
private fun PresetContextMenu(
    preset: PresetWithThumbnail,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                ContextMenuRow(Icons.Default.Check, stringRes { presetApply }, onApply, onDismiss)
                ContextMenuRow(Icons.Default.Edit, stringRes { presetEdit }, onEdit, onDismiss)
                ContextMenuRow(Icons.Default.FileUpload, stringRes { presetExport }, onExport, onDismiss)
                ContextMenuRow(
                    Icons.Default.Delete,
                    stringRes { presetDelete },
                    onDelete,
                    onDismiss,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}

@Composable
private fun ContextMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = {
                onDismiss()
                onClick()
            }
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, color = tint)
        }
    }
}

// ================================================================
// Before/after preview overlay
// ================================================================

@Composable
private fun BeforeAfterPreviewOverlay(
    preset: PresetWithThumbnail,
    presetService: PresetService,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Generate the "before" (default-params) thumbnail lazily.
    var beforeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        beforeBitmap = presetService.renderPreview(PipelineParams())
    }

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.82f),
        onClick = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                preset.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Before
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        beforeBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringRes { compareBefore },
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } ?: CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringRes { compareBefore },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                // After
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        preset.thumbnail?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringRes { compareAfter },
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } ?: CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringRes { compareAfter },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                preset.category.ifBlank { stringRes { presetCategoryCustom } },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// ================================================================
// Name + category dialog (used for create & edit)
// ================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetNameCategoryDialog(
    title: String,
    initialName: String,
    initialCategory: String,
    confirmLabel: String,
    allowUpdateParams: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, updateParams: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var updateParams by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringRes { presetNamePrompt }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text(stringRes { presetCategoryPrompt }) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        PRESET_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                if (allowUpdateParams) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = updateParams, onCheckedChange = { updateParams = it })
                        Text(
                            stringRes { presetCreate },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, category, updateParams) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}
