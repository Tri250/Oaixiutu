package com.alcedo.studio.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.data.model.*
import com.alcedo.studio.ui.common.ProgressBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    navController: NavController,
    imageId: String
) {
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableIntStateOf(95) }
    var colorSpace by remember { mutableStateOf(ColorSpace.SRGB) }
    var embedIcc by remember { mutableStateOf(true) }
    var includeMetadata by remember { mutableStateOf(true) }
    var isHdr by remember { mutableStateOf(false) }
    var maxDimension by remember { mutableStateOf("") }
    var bitDepth by remember { mutableIntStateOf(8) }
    var outputPath by remember { mutableStateOf("/sdcard/Pictures/export") }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var showBatchExport by remember { mutableStateOf(false) }
    var batchImages by remember { mutableStateOf(listOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Format
            Text("Format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ExportFormat.entries.forEach { f ->
                    FilterChip(
                        selected = format == f,
                        onClick = { format = f },
                        label = { Text(f.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Quality
            if (format == ExportFormat.JPEG || format == ExportFormat.ULTRA_HDR) {
                Text("Quality: $quality%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.toInt() },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Bit depth
            if (format == ExportFormat.PNG || format == ExportFormat.TIFF) {
                Text("Bit Depth", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = bitDepth == 8,
                        onClick = { bitDepth = 8 },
                        label = { Text("8-bit") }
                    )
                    FilterChip(
                        selected = bitDepth == 16,
                        onClick = { bitDepth = 16 },
                        label = { Text("16-bit") }
                    )
                }
            }

            // Color Space
            Text("Color Space", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(ColorSpace.SRGB, ColorSpace.DISPLAY_P3, ColorSpace.REC2020, ColorSpace.ACES)
                    .forEach { cs ->
                        FilterChip(
                            selected = colorSpace == cs,
                            onClick = { colorSpace = cs },
                            label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
            }

            // Options
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = embedIcc, onCheckedChange = { embedIcc = it })
                Text("Embed ICC Profile", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it })
                Text("Include Metadata", style = MaterialTheme.typography.bodyMedium)
            }
            if (format == ExportFormat.ULTRA_HDR) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isHdr, onCheckedChange = { isHdr = it })
                    Text("HDR Output", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Max dimension
            OutlinedTextField(
                value = maxDimension,
                onValueChange = { maxDimension = it.filter { c -> c.isDigit() } },
                label = { Text("Max dimension (px)") },
                placeholder = { Text("No limit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) }
            )

            // Output path
            OutlinedTextField(
                value = outputPath,
                onValueChange = { outputPath = it },
                label = { Text("Output path") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { /* Open directory picker */ }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Browse")
                    }
                }
            )

            // Batch export
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Batch Export", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = showBatchExport,
                    onCheckedChange = { showBatchExport = it }
                )
            }
            if (showBatchExport) {
                Text(
                    "Select images from album to batch export",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (batchImages.isNotEmpty()) {
                    Text(
                        "${batchImages.size} images selected",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedButton(
                    onClick = { /* Add images from album */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Images")
                }
            }

            // Export progress
            if (isExporting) {
                ProgressBar(
                    progress = exportProgress,
                    label = "Exporting...",
                    onCancel = { isExporting = false }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Export button
            Button(
                onClick = {
                    isExporting = true
                    // Simulate export progress
                    kotlinx.coroutines.MainScope().let { scope ->
                        // Export would happen here
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export ${if (showBatchExport) "${batchImages.size} " else ""}Image${if (showBatchExport && batchImages.size != 1) "s" else ""}")
            }
        }
    }
}