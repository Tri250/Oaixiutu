package com.alcedo.studio.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.preset.BuiltinPreset
import com.alcedo.studio.data.preset.PresetCategory
import com.alcedo.studio.domain.service.PresetService
import com.alcedo.studio.domain.service.PresetWithThumbnail
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.theme.AlcedoElevation
import com.alcedo.studio.ui.theme.AlcedoFontRoles
import com.alcedo.studio.ui.theme.AlcedoRadius
import com.alcedo.studio.ui.theme.AlcedoSpacing
import com.alcedo.studio.ui.theme.LocalAlcedoColors
import com.alcedo.studio.viewmodel.EditorViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Categories selectable in the create/edit preset dialog.
 * Includes the two new categories: Vintage and Creative.
 */
private val PRESET_CATEGORIES = listOf(
    PresetService.CATEGORY_GENERAL,
    PresetService.CATEGORY_PORTRAIT,
    PresetService.CATEGORY_LANDSCAPE,
    PresetService.CATEGORY_FILM,
    PresetService.CATEGORY_STREET,
    PresetService.CATEGORY_VINTAGE,
    PresetService.CATEGORY_BW,
    PresetService.CATEGORY_CREATIVE
)

private fun categoryColor(category: String): Color = when (category) {
    PresetService.CATEGORY_FILM -> Color(0xFFE0A040)
    PresetService.CATEGORY_PORTRAIT -> Color(0xFFE07A9A)
    PresetService.CATEGORY_LANDSCAPE -> Color(0xFF5CB670)
    PresetService.CATEGORY_BW -> Color(0xFF9E9E9E)
    PresetService.CATEGORY_STREET -> Color(0xFFB07ACC)
    PresetService.CATEGORY_VINTAGE -> Color(0xFFC4956A)
    PresetService.CATEGORY_CREATIVE -> Color(0xFF5CB6B6)
    PresetService.CATEGORY_IMPORTED -> Color(0xFF5C9BB6)
    PresetService.CATEGORY_LUT -> Color(0xFFE07A3A)
    else -> Color(0xFF5C9BB6)
}

private fun presetCategoryColor(category: PresetCategory): Color = when (category) {
    PresetCategory.Basic -> Color(0xFF5C9BB6)
    PresetCategory.Film -> Color(0xFFE0A040)
    PresetCategory.Portrait -> Color(0xFFE07A9A)
    PresetCategory.Landscape -> Color(0xFF5CB670)
    PresetCategory.Street -> Color(0xFFB07ACC)
    PresetCategory.Vintage -> Color(0xFFC4956A)
    PresetCategory.BnW -> Color(0xFF9E9E9E)
    PresetCategory.Creative -> Color(0xFF5CB6B6)
}

/**
 * Redesigned preset management panel with category tabs and built-in preset library.
 *
 * Features:
 * - Category tabs: Horizontal scrollable tabs for each PresetCategory
 * - Preset grid: 3-column grid of preset thumbnails with names
 * - Apply on tap: Apply preset with a single tap
 * - Long press menu: Edit/Delete/Share/Export for user presets
 * - Import button: Import .alcedo-preset / .json / .xmp / .cube files
 * - Create preset button: Save current settings as a new preset
 * - Built-in presets: Available immediately from BuiltinPresets
 * - User presets: Persisted in Room database
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PresetPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val presetService = remember { viewModel.presetService }
    val alcedoColors = LocalAlcedoColors.current

    // Database-backed presets (user presets + previously seeded built-in presets)
    val dbPresets by presetService.getAllPresets()
        .catch { emit(emptyList()) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val currentPreview by viewModel.previewBitmap.collectAsStateWithLifecycle()

    // Built-in presets from BuiltinPresets (available immediately)
    val builtinPresets = remember { presetService.getAllBuiltinPresets() }

    // Selected category tab
    var selectedCategory by remember { mutableStateOf<PresetCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuPreset by remember { mutableStateOf<PresetWithThumbnail?>(null) }
    var contextMenuBuiltin by remember { mutableStateOf<BuiltinPreset?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PresetWithThumbnail?>(null) }
    var deleteTarget by remember { mutableStateOf<PresetWithThumbnail?>(null) }
    var exportTarget by remember { mutableStateOf<PresetWithThumbnail?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Precompute string resources
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

    // Import launcher — picks a preset file (.json / .xmp / .cube / .alcedo-preset)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                try {
                    val id = viewModel.importPreset(uri)
                    snackbarMessage = if (id > 0) importedMsg else errorMsg
                } catch (_: Throwable) {
                    snackbarMessage = errorMsg
                } finally {
                    isBusy = false
                }
            }
        }
    }

    // Export launcher — SAF "create file" for a single preset as .json
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val target = exportTarget
        if (uri != null && target != null) {
            scope.launch {
                isBusy = true
                try {
                    val ok = viewModel.exportPreset(target.id, uri)
                    snackbarMessage = if (ok) exportedFmt.format(target.name)
                    else errorMsg
                } catch (_: Throwable) {
                    snackbarMessage = errorMsg
                } finally {
                    isBusy = false
                }
            }
        }
        exportTarget = null
    }

    // ── Compute filtered presets ──
    // When a category is selected, show built-in presets from that category
    // plus matching user presets from the database.
    val filteredBuiltinPresets = remember(builtinPresets, selectedCategory, searchQuery) {
        val byCategory = if (selectedCategory != null) {
            builtinPresets.filter { it.category == selectedCategory }
        } else {
            builtinPresets
        }
        if (searchQuery.isBlank()) byCategory
        else byCategory.filter {
            it.nameZh.contains(searchQuery, ignoreCase = true) ||
                it.nameEn.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredDbPresets = remember(dbPresets, selectedCategory, searchQuery) {
        val category = selectedCategory
        val byCategory = if (category != null) {
            val categoryStr = PresetService.categoryToString(category)
            dbPresets.filter { it.category == categoryStr }
        } else {
            dbPresets
        }
        if (searchQuery.isBlank()) byCategory
        else byCategory.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val hasAnyPresets = filteredBuiltinPresets.isNotEmpty() || filteredDbPresets.isNotEmpty()

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
        ) {
            // ── Top action bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
            ) {
                // "Save current as preset"
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringRes { presetCreate },
                        style = AlcedoFontRoles.uiOverline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Import (.json / .xmp / .cube / .alcedo-preset)
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/json",
                                "text/xml",
                                "application/xml",
                                "text/plain",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringRes { presetImport },
                        style = AlcedoFontRoles.uiOverline,
                        maxLines = 1
                    )
                }
            }

            // ── Search bar ──
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringRes { presetSearch }, style = AlcedoFontRoles.uiCaption) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = AlcedoFontRoles.uiCaption
            )

            // ── Category tabs ──
            ScrollableTabRow(
                selectedTabIndex = selectedCategory?.let { cat ->
                    PresetCategory.entries.indexOf(cat) + 1 // +1 for "All" tab
                } ?: 0,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = AlcedoSpacing.xs
            ) {
                // "All" tab
                Tab(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    text = {
                        Text(
                            stringRes { presetCategoryAll },
                            style = AlcedoFontRoles.uiOverline,
                            maxLines = 1
                        )
                    }
                )
                // Category tabs
                PresetCategory.entries.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = {
                            Text(
                                category.labelEn,
                                style = AlcedoFontRoles.uiOverline,
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // ── Built-in presets section ──
            if (filteredBuiltinPresets.isNotEmpty()) {
                // Section header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = alcedoColors.primary
                    )
                    Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                    Text(
                        stringRes { presetCategoryBuiltin },
                        style = AlcedoFontRoles.uiOverline,
                        color = alcedoColors.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Built-in preset grid (3 columns)
                val builtinRows = filteredBuiltinPresets.chunked(3)
                builtinRows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
                    ) {
                        rowPresets.forEach { preset ->
                            BuiltinPresetCard(
                                preset = preset,
                                modifier = Modifier.weight(1f),
                                onTap = {
                                    HapticFeedback.click(view)
                                    viewModel.applyBuiltinPreset(preset)
                                    snackbarMessage = preset.nameZh
                                },
                                onLongPress = {
                                    HapticFeedback.heavyClick(view)
                                    contextMenuBuiltin = preset
                                }
                            )
                        }
                        repeat(3 - rowPresets.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── User presets section ──
            if (filteredDbPresets.isNotEmpty()) {
                // Section header (only show if built-in presets are also visible)
                if (filteredBuiltinPresets.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = alcedoColors.textMuted
                        )
                        Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                        Text(
                            stringRes { presetCategoryCustom },
                            style = AlcedoFontRoles.uiOverline,
                            color = alcedoColors.textMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // User preset grid (3 columns)
                val userRows = filteredDbPresets.chunked(3)
                userRows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
                    ) {
                        rowPresets.forEach { preset ->
                            PresetCard(
                                preset = preset,
                                modifier = Modifier.weight(1f),
                                onTap = {
                                    HapticFeedback.click(view)
                                    viewModel.applyPreset(preset)
                                    snackbarMessage = preset.name
                                },
                                onLongPress = {
                                    HapticFeedback.heavyClick(view)
                                    contextMenuPreset = preset
                                }
                            )
                        }
                        repeat(3 - rowPresets.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Empty state ──
            if (!hasAnyPresets) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AlcedoSpacing.xxxl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = alcedoColors.textMuted.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(AlcedoSpacing.sm))
                        Text(
                            stringRes { presetTitle },
                            style = AlcedoFontRoles.uiCaption,
                            color = alcedoColors.textMuted.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // ── Busy scrim ──
        if (isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(alcedoColors.scrim.copy(alpha = 0.35f)),
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

    // ── Context menu for built-in presets (Apply only) ──
    val menuBuiltin = contextMenuBuiltin
    if (menuBuiltin != null) {
        AlertDialog(
            onDismissRequest = { contextMenuBuiltin = null },
            title = { Text(menuBuiltin.nameZh, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ContextMenuRow(Icons.Default.Check, stringRes { presetApply }, {
                        contextMenuBuiltin = null
                        viewModel.applyBuiltinPreset(menuBuiltin)
                        snackbarMessage = menuBuiltin.nameZh
                    }, { contextMenuBuiltin = null })
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuBuiltin = null }) { Text(stringRes { cancel }) }
            }
        )
    }

    // ── Context menu for user presets (Apply / Edit / Delete / Export) ──
    val menuPreset = contextMenuPreset
    if (menuPreset != null) {
        PresetContextMenu(
            preset = menuPreset,
            onDismiss = { contextMenuPreset = null },
            onApply = {
                contextMenuPreset = null
                viewModel.applyPreset(menuPreset)
                snackbarMessage = menuPreset.name
            },
            onEdit = {
                editTarget = menuPreset
                contextMenuPreset = null
            },
            onExport = {
                exportTarget = menuPreset
                contextMenuPreset = null
                val safeName = (menuPreset.name ?: "preset")
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                exportLauncher.launch("$safeName.json")
            },
            onDelete = {
                deleteTarget = menuPreset
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
            initialDescription = "",
            confirmLabel = stringRes { presetCreate },
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, category, description, _ ->
                showCreateDialog = false
                scope.launch {
                    isBusy = true
                    try {
                        presetService.createPreset(
                            name = name,
                            category = category,
                            params = viewModel.params.value,
                            thumbnailBitmap = currentPreview,
                            description = description
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
    val editPreset = editTarget
    if (editPreset != null) {
        PresetNameCategoryDialog(
            title = stringRes { presetEdit },
            initialName = editPreset.name,
            initialCategory = editPreset.category,
            initialDescription = editPreset.description,
            confirmLabel = stringRes { presetEdit },
            allowUpdateParams = !editPreset.isBuiltIn,
            onDismiss = { editTarget = null },
            onConfirm = { name, category, description, updateParams ->
                editTarget = null
                scope.launch {
                    isBusy = true
                    try {
                        val params = if (updateParams) viewModel.params.value else editPreset.params
                        presetService.updatePreset(editPreset.id, name, category, params, description)
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
    val deletePreset = deleteTarget
    if (deletePreset != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringRes { presetDelete }) },
            text = { Text(deletePreset.name) },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            isBusy = true
                            try {
                                presetService.deletePreset(deletePreset.id)
                                snackbarMessage = deletedMsg
                            } catch (_: Throwable) {
                                snackbarMessage = errorMsg
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = alcedoColors.danger)
                ) { Text(stringRes { presetDelete }) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringRes { cancel }) }
            }
        )
    }
}

// ================================================================
// Built-in preset card
// ================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BuiltinPresetCard(
    preset: BuiltinPreset,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val alcedoColors = LocalAlcedoColors.current
    val catColor = presetCategoryColor(preset.category)

    Surface(
        modifier = modifier
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(AlcedoRadius.sm),
        color = alcedoColors.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = AlcedoElevation.level1.dp
    ) {
        Column(
            modifier = Modifier.padding(AlcedoSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thumbnail placeholder with category color accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(AlcedoRadius.xs))
                    .background(alcedoColors.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                // Category-colored accent gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            catColor.copy(alpha = 0.12f)
                        )
                )
                // Built-in badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .background(
                            alcedoColors.scrim.copy(alpha = 0.55f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        stringRes { presetCategoryBuiltin },
                        style = AlcedoFontRoles.uiOverline,
                        fontSize = TextUnit(9f, TextUnitType.Sp),
                        color = alcedoColors.text
                    )
                }
                // Category icon
                Icon(
                    imageVector = when (preset.category) {
                        PresetCategory.Basic -> Icons.Default.Tune
                        PresetCategory.Film -> Icons.Default.Movie
                        PresetCategory.Portrait -> Icons.Default.Face
                        PresetCategory.Landscape -> Icons.Default.Landscape
                        PresetCategory.Street -> Icons.Default.LocationCity
                        PresetCategory.Vintage -> Icons.Default.History
                        PresetCategory.BnW -> Icons.Default.Contrast
                        PresetCategory.Creative -> Icons.Default.Palette
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = catColor.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
            // Preset name
            Text(
                text = preset.nameZh,
                style = AlcedoFontRoles.uiOverline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            // Category badge
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(catColor.copy(alpha = 0.18f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = preset.category.labelEn,
                    style = AlcedoFontRoles.uiOverline,
                    fontSize = TextUnit(9f, TextUnitType.Sp),
                    color = catColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ================================================================
// User preset card (database-backed, with real thumbnail)
// ================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    preset: PresetWithThumbnail,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val alcedoColors = LocalAlcedoColors.current
    Surface(
        modifier = modifier
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(AlcedoRadius.sm),
        color = alcedoColors.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = AlcedoElevation.level1.dp
    ) {
        Column(
            modifier = Modifier.padding(AlcedoSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thumbnail (REAL pipeline-rendered bitmap)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(AlcedoRadius.xs))
                    .background(alcedoColors.surfaceContainerLowest),
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
                                alcedoColors.scrim.copy(alpha = 0.55f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            stringRes { presetCategoryBuiltin },
                            style = AlcedoFontRoles.uiOverline,
                            fontSize = TextUnit(9f, TextUnitType.Sp),
                            color = alcedoColors.text
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
            // Preset name
            Text(
                text = preset.name,
                style = AlcedoFontRoles.uiOverline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            // Description (optional)
            if (preset.description.isNotBlank()) {
                Text(
                    text = preset.description,
                    style = AlcedoFontRoles.uiOverline,
                    fontSize = TextUnit(9f, TextUnitType.Sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = alcedoColors.textMuted.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                )
            }
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
                    style = AlcedoFontRoles.uiOverline,
                    fontSize = TextUnit(9f, TextUnitType.Sp),
                    color = catColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ================================================================
// Context menu (Apply / Edit / Delete / Export)
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
                    tint = LocalAlcedoColors.current.danger
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
    tint: Color = LocalAlcedoColors.current.text
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AlcedoSpacing.xs),
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
// Name + category + description dialog (used for create & edit)
// ================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetNameCategoryDialog(
    title: String,
    initialName: String,
    initialCategory: String,
    initialDescription: String,
    confirmLabel: String,
    allowUpdateParams: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, description: String, updateParams: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var description by remember { mutableStateOf(initialDescription) }
    var updateParams by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)) {
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
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringRes { presetDescriptionPrompt }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (allowUpdateParams) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = updateParams, onCheckedChange = { updateParams = it })
                        Text(
                            stringRes { presetCreate },
                            style = AlcedoFontRoles.uiCaption
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, category, description, updateParams) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}
