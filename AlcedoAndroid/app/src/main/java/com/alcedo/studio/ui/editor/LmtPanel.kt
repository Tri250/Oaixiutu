package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader

enum class BuiltInLutPreset(val displayName: String) {
    KODAK_PORTRA("Kodak Portra"),
    FUJI_PRO("Fuji Pro"),
    AGFA_VISTA("Agfa Vista"),
    ILFORD_HP5("Ilford HP5"),
    KODAK_EKTAR("Kodak Ektar"),
    FUJI_VELVIA("Fuji Velvia"),
    CINE_STILL("CineStill 800T"),
    KODACHROME("Kodachrome 64")
}

@Composable
fun LmtPanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLutBrowser by remember { mutableStateOf(false) }
    var lutSearchQuery by remember { mutableStateOf("") }
    var lutIntensity by remember { mutableStateOf(100f) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Enable / Disable
        SectionHeader(
            title = "LMT (Look Modify Transform)",
            trailing = {
                Switch(
                    checked = params.lutEnabled,
                    onCheckedChange = { onParamsChanged(params.copy(lutEnabled = it)) }
                )
            }
        ) {
            if (params.lutEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // LUT file selector / import
                    OutlinedButton(
                        onClick = { showLutBrowser = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (params.lutPath.isEmpty()) "Import LUT File..."
                            else params.lutPath.substringAfterLast('/')
                        )
                    }

                    // Active LUT display name
                    if (params.lutPath.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Active: ${params.lutPath.substringAfterLast('/')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Intensity / Opacity slider
                    AdjustmentSlider(
                        label = "Intensity",
                        value = lutIntensity,
                        range = 0f..100f,
                        onValueChange = { lutIntensity = it },
                        defaultValue = 100f,
                        valueDisplayTransform = { "${it.toInt()}%" }
                    )
                }
            }
        }

        // Built-in LUT Presets
        SectionHeader(title = "Built-in LUT Presets") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BuiltInLutPreset.entries.forEach { preset ->
                    val isSelected = params.lutPath == preset.displayName
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = {
                            onParamsChanged(
                                params.copy(
                                    lutPath = preset.displayName,
                                    lutEnabled = true
                                )
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    preset.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // LUT Browser Dialog
    if (showLutBrowser) {
        AlertDialog(
            onDismissRequest = { showLutBrowser = false },
            title = { Text("Import LUT File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lutSearchQuery,
                        onValueChange = { lutSearchQuery = it },
                        placeholder = { Text("Search LUTs...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    val filteredLuts = BuiltInLutPreset.entries.filter {
                        lutSearchQuery.isEmpty() ||
                            it.displayName.contains(lutSearchQuery, ignoreCase = true)
                    }
                    filteredLuts.forEach { preset ->
                        ListItem(
                            headlineContent = { Text(preset.displayName) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = {
                                IconButton(onClick = {
                                    onParamsChanged(
                                        params.copy(
                                            lutPath = preset.displayName,
                                            lutEnabled = true
                                        )
                                    )
                                    showLutBrowser = false
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Select")
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLutBrowser = false }) {
                    Text("Close")
                }
            }
        )
    }
}
