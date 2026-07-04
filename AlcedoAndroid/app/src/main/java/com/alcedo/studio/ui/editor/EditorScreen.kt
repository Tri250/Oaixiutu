package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navController: NavController,
    imageId: String,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModelFactory(imageId))
) {
    val image by viewModel.imageModel.collectAsState()
    val preview by viewModel.previewBitmap.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val params = viewModel.params.value
    var selectedPanel by remember { mutableStateOf(EditorPanel.BASIC) }
    var showExport by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(image?.imageName ?: "Editor") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { viewModel.commitChanges() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    IconButton(onClick = { showExport = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Image preview area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Handle zoom/pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.9f)
                            .background(Color.DarkGray)
                    ) {
                        Text(
                            "Image Preview\n${image?.imagePath ?: ""}",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // Editor panels
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedPanel.ordinal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EditorPanel.entries.forEach { panel ->
                        Tab(
                            selected = selectedPanel == panel,
                            onClick = { selectedPanel = panel },
                            text = { Text(panel.label) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedPanel) {
                        EditorPanel.BASIC -> BasicAdjustmentsPanel(params, viewModel)
                        EditorPanel.COLOR -> ColorPanel(params, viewModel)
                        EditorPanel.GEOMETRY -> GeometryPanel(params, viewModel)
                        EditorPanel.RAW -> RawDecodePanel(params, viewModel)
                        EditorPanel.EFFECTS -> EffectsPanel(params, viewModel)
                        EditorPanel.HISTORY -> HistoryPanel(viewModel)
                    }
                }
            }
        }
    }

    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onExport = { settings ->
                viewModel.export(settings)
                showExport = false
            }
        )
    }
}

@Composable
private fun BasicAdjustmentsPanel(params: PipelineParams, viewModel: EditorViewModel) {
    AdjustmentSlider("Exposure", params.exposure, -3f..3f) {
        viewModel.updateParams(params.copy(exposure = it))
    }
    AdjustmentSlider("Contrast", params.contrast, -1f..1f) {
        viewModel.updateParams(params.copy(contrast = it))
    }
    AdjustmentSlider("Highlights", params.highlights, -1f..1f) {
        viewModel.updateParams(params.copy(highlights = it))
    }
    AdjustmentSlider("Shadows", params.shadows, -1f..1f) {
        viewModel.updateParams(params.copy(shadows = it))
    }
    AdjustmentSlider("Saturation", params.saturation, -1f..1f) {
        viewModel.updateParams(params.copy(saturation = it))
    }
    AdjustmentSlider("Vibrance", params.vibrance, -1f..1f) {
        viewModel.updateParams(params.copy(vibrance = it))
    }
}

@Composable
private fun ColorPanel(params: PipelineParams, viewModel: EditorViewModel) {
    AdjustmentSlider("Temperature", params.whiteBalanceTemp, 2000f..15000f) {
        viewModel.updateParams(params.copy(whiteBalanceTemp = it))
    }
    AdjustmentSlider("Tint", params.whiteBalanceTint, -100f..100f) {
        viewModel.updateParams(params.copy(whiteBalanceTint = it))
    }
    // Tone curve, HSL, Color wheels would go here
}

@Composable
private fun GeometryPanel(params: PipelineParams, viewModel: EditorViewModel) {
    AdjustmentSlider("Rotate", params.geometryRotate, -45f..45f) {
        viewModel.updateParams(params.copy(geometryRotate = it))
    }
    Text("Crop", style = MaterialTheme.typography.labelLarge)
    AdjustmentSlider("Left", params.geometryCropLeft, 0f..1f) {
        viewModel.updateParams(params.copy(geometryCropLeft = it))
    }
    AdjustmentSlider("Top", params.geometryCropTop, 0f..1f) {
        viewModel.updateParams(params.copy(geometryCropTop = it))
    }
    AdjustmentSlider("Right", params.geometryCropRight, 0f..1f) {
        viewModel.updateParams(params.copy(geometryCropRight = it))
    }
    AdjustmentSlider("Bottom", params.geometryCropBottom, 0f..1f) {
        viewModel.updateParams(params.copy(geometryCropBottom = it))
    }
}

@Composable
private fun RawDecodePanel(params: PipelineParams, viewModel: EditorViewModel) {
    Text("Demosaic", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DemosaicAlgorithm.entries.forEach { algo ->
            FilterChip(
                selected = params.rawDecodeParams.demosaicAlgorithm == algo,
                onClick = {
                    viewModel.updateParams(
                        params.copy(rawDecodeParams = params.rawDecodeParams.copy(demosaicAlgorithm = algo))
                    )
                },
                label = { Text(algo.name) }
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = params.rawDecodeParams.highlightReconstruction,
            onCheckedChange = {
                viewModel.updateParams(
                    params.copy(rawDecodeParams = params.rawDecodeParams.copy(highlightReconstruction = it))
                )
            }
        )
        Text("Highlight Reconstruction")
    }
}

@Composable
private fun EffectsPanel(params: PipelineParams, viewModel: EditorViewModel) {
    AdjustmentSlider("Film Grain", params.filmGrainIntensity, 0f..1f) {
        viewModel.updateParams(params.copy(filmGrainIntensity = it))
    }
    AdjustmentSlider("Halation", params.halationIntensity, 0f..1f) {
        viewModel.updateParams(params.copy(halationIntensity = it))
    }
    AdjustmentSlider("Sharpen", params.sharpenAmount, 0f..2f) {
        viewModel.updateParams(params.copy(sharpenAmount = it))
    }
    AdjustmentSlider("Clarity", params.clarityAmount, 0f..1f) {
        viewModel.updateParams(params.copy(clarityAmount = it))
    }
}

@Composable
private fun HistoryPanel(viewModel: EditorViewModel) {
    val history by viewModel.history.collectAsState()
    val versions = history?.versionStorage?.values?.toList() ?: emptyList()

    Text("Versions", style = MaterialTheme.typography.titleMedium)
    versions.forEach { version ->
        ListItem(
            headlineContent = { Text(version.displayName) },
            supportingContent = { Text("${version.transactions.size} edits") },
            trailingContent = {
                if (history?.activeVersionId == version.versionId) {
                    Icon(Icons.Default.Check, contentDescription = "Active")
                }
            }
        )
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportSettings) -> Unit
) {
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableStateOf(95) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Format")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { f ->
                        FilterChip(
                            selected = format == f,
                            onClick = { format = f },
                            label = { Text(f.name) }
                        )
                    }
                }
                if (format == ExportFormat.JPEG || format == ExportFormat.ULTRA_HDR) {
                    Text("Quality: $quality%")
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt() },
                        valueRange = 1f..100f
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExport(ExportSettings(format = format, quality = quality)) }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private enum class EditorPanel(val label: String) {
    BASIC("Basic"),
    COLOR("Color"),
    GEOMETRY("Geometry"),
    RAW("RAW"),
    EFFECTS("Effects"),
    HISTORY("History")
}
