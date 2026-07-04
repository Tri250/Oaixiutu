package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader

@Composable
fun EffectsPanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLutBrowser by remember { mutableStateOf(false) }
    var lutSearchQuery by remember { mutableStateOf("") }

    // Sample LUT list
    val sampleLuts = remember {
        listOf(
            "Cinematic Teal",
            "Warm Vintage",
            "Black & White",
            "Film Emulation",
            "Moody Blue",
            "Golden Hour",
            "Cross Process",
            "Bleach Bypass"
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Film Grain
        SectionHeader(title = "Film Grain") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Intensity",
                    value = params.filmGrainIntensity,
                    range = 0f..1f,
                    onValueChange = { onParamsChanged(params.copy(filmGrainIntensity = it)) },
                    defaultValue = 0f
                )
            }
        }

        // Halation
        SectionHeader(title = "Halation") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Intensity",
                    value = params.halationIntensity,
                    range = 0f..1f,
                    onValueChange = { onParamsChanged(params.copy(halationIntensity = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Spread",
                    value = params.halationSpread,
                    range = 0f..50f,
                    onValueChange = { onParamsChanged(params.copy(halationSpread = it)) },
                    defaultValue = 10f,
                    valueDisplayTransform = { "%.0f".format(it) }
                )
                AdjustmentSlider(
                    label = "Threshold",
                    value = params.halationThreshold,
                    range = 0f..1f,
                    onValueChange = { onParamsChanged(params.copy(halationThreshold = it)) },
                    defaultValue = 0.8f
                )
                AdjustmentSlider(
                    label = "Red Bias",
                    value = params.halationRedBias,
                    range = 0f..1f,
                    onValueChange = { onParamsChanged(params.copy(halationRedBias = it)) },
                    defaultValue = 0.7f
                )
            }
        }

        // Sharpen
        SectionHeader(title = "Sharpen") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Amount",
                    value = params.sharpenAmount,
                    range = 0f..2f,
                    onValueChange = { onParamsChanged(params.copy(sharpenAmount = it)) },
                    defaultValue = 0f
                )
            }
        }

        // Clarity
        SectionHeader(title = "Clarity") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentSlider(
                    label = "Amount",
                    value = params.clarityAmount,
                    range = 0f..1f,
                    onValueChange = { onParamsChanged(params.copy(clarityAmount = it)) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Radius",
                    value = params.clarityRadius,
                    range = 1f..50f,
                    onValueChange = { onParamsChanged(params.copy(clarityRadius = it)) },
                    defaultValue = 15f,
                    valueDisplayTransform = { "%.0f".format(it) }
                )
            }
        }

        // LUT
        SectionHeader(
            title = "LUT",
            trailing = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(
                        checked = params.lutEnabled,
                        onCheckedChange = { onParamsChanged(params.copy(lutEnabled = it)) }
                    )
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (params.lutEnabled) {
                    OutlinedButton(
                        onClick = { showLutBrowser = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (params.lutPath.isEmpty()) "Select LUT..."
                            else params.lutPath.substringAfterLast('/')
                        )
                    }
                }
            }
        }
    }

    // LUT Browser Dialog
    if (showLutBrowser) {
        AlertDialog(
            onDismissRequest = { showLutBrowser = false },
            title = { Text("LUT Browser") },
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
                    val filteredLuts = sampleLuts.filter {
                        lutSearchQuery.isEmpty() || it.contains(lutSearchQuery, ignoreCase = true)
                    }
                    filteredLuts.forEach { lutName ->
                        ListItem(
                            headlineContent = { Text(lutName) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingContent = {
                                IconButton(onClick = {
                                    onParamsChanged(params.copy(lutPath = lutName))
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