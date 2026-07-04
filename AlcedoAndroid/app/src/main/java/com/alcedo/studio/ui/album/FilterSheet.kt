package com.alcedo.studio.ui.album

import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    onDismiss: () -> Unit,
    onApply: (FilterState) -> Unit,
    onReset: () -> Unit
) {
    var cameraMakes by remember { mutableStateOf(setOf<String>()) }
    var cameraModels by remember { mutableStateOf(setOf<String>()) }
    var lensModel by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var selectedRating by remember { mutableIntStateOf(0) }
    var selectedFileTypes by remember { mutableStateOf(setOf<String>()) }
    var selectedAiLabels by remember { mutableStateOf(setOf<String>()) }
    var filterPresetName by remember { mutableStateOf("") }
    var showSavePreset by remember { mutableStateOf(false) }

    val sampleCameraMakes = listOf("Sony", "Canon", "Nikon", "Fujifilm", "Leica", "Panasonic", "Olympus")
    val sampleCameraModels = listOf("α7R V", "EOS R5", "Z8", "X-T5", "M11", "S5 II", "OM-1")
    val sampleFileTypes = listOf("JPEG", "PNG", "TIFF", "RAW", "DNG")
    val sampleAiLabels = listOf("Portrait", "Landscape", "Night", "City", "Nature", "Food", "Street", "Abstract")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            Row {
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
                TextButton(onClick = {
                    onApply(
                        FilterState(
                            cameraMakes = cameraMakes.toList(),
                            cameraModels = cameraModels.toList(),
                            lensModel = lensModel,
                            startDate = startDate,
                            endDate = endDate,
                            rating = selectedRating,
                            fileTypes = selectedFileTypes.toList(),
                            aiLabels = selectedAiLabels.toList()
                        )
                    )
                }) {
                    Text("Apply")
                }
            }
        }

        // Camera Make
        Text("Camera Make", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sampleCameraMakes.forEach { make ->
                FilterChip(
                    selected = cameraMakes.contains(make),
                    onClick = {
                        cameraMakes = if (cameraMakes.contains(make))
                            cameraMakes - make
                        else cameraMakes + make
                    },
                    label = { Text(make, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Camera Model
        Text("Camera Model", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sampleCameraModels.forEach { model ->
                FilterChip(
                    selected = cameraModels.contains(model),
                    onClick = {
                        cameraModels = if (cameraModels.contains(model))
                            cameraModels - model
                        else cameraModels + model
                    },
                    label = { Text(model, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Lens
        Text("Lens Model", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = lensModel,
            onValueChange = { lensModel = it },
            placeholder = { Text("Any lens...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )

        // Date Range
        Text("Date Range", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                placeholder = { Text("Start") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                placeholder = { Text("End") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Rating
        Text("Rating", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..5).forEach { star ->
                IconButton(
                    onClick = { selectedRating = if (selectedRating == star) 0 else star },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarOutline,
                        contentDescription = "$star stars",
                        tint = if (star <= selectedRating)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (selectedRating > 0) {
                Text("& up", style = MaterialTheme.typography.bodySmall)
            }
        }

        // File Type
        Text("File Type", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sampleFileTypes.forEach { type ->
                FilterChip(
                    selected = selectedFileTypes.contains(type),
                    onClick = {
                        selectedFileTypes = if (selectedFileTypes.contains(type))
                            selectedFileTypes - type
                        else selectedFileTypes + type
                    },
                    label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // AI Labels
        Text("AI Labels", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sampleAiLabels.take(4).forEach { label ->
                FilterChip(
                    selected = selectedAiLabels.contains(label),
                    onClick = {
                        selectedAiLabels = if (selectedAiLabels.contains(label))
                            selectedAiLabels - label
                        else selectedAiLabels + label
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sampleAiLabels.drop(4).forEach { label ->
                FilterChip(
                    selected = selectedAiLabels.contains(label),
                    onClick = {
                        selectedAiLabels = if (selectedAiLabels.contains(label))
                            selectedAiLabels - label
                        else selectedAiLabels + label
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Save filter preset
        HorizontalDivider()
        OutlinedButton(
            onClick = { showSavePreset = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save as Filter Preset")
        }
    }

    if (showSavePreset) {
        AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("Save Filter Preset") },
            text = {
                OutlinedTextField(
                    value = filterPresetName,
                    onValueChange = { filterPresetName = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    // Save preset
                    showSavePreset = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePreset = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class FilterState(
    val cameraMakes: List<String> = emptyList(),
    val cameraModels: List<String> = emptyList(),
    val lensModel: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val rating: Int = 0,
    val fileTypes: List<String> = emptyList(),
    val aiLabels: List<String> = emptyList()
)