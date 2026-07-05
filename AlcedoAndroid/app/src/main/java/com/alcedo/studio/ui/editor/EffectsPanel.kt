package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun EffectsPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val params by remember { viewModel.params }
    var showLutBrowser by remember { mutableStateOf(false) }
    var lutSearchQuery by remember { mutableStateOf("") }

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
        // ── Film Grain ─────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Film Grain",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { viewModel.updateFilmGrain(0f) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Grain",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = "Intensity",
                    value = params.filmGrainIntensity,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateFilmGrain(it) },
                    defaultValue = 0f
                )
            }
        }

        // ── Halation ──────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Halation",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { viewModel.updateHalation(0f, 0.8f, 10f, 0.7f) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Halation",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = "Intensity",
                    value = params.halationIntensity,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateHalation(
                            it, params.halationThreshold,
                            params.halationSpread, params.halationRedBias
                        )
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Spread",
                    value = params.halationSpread,
                    range = 0f..50f,
                    onValueChange = {
                        viewModel.updateHalation(
                            params.halationIntensity, params.halationThreshold,
                            it, params.halationRedBias
                        )
                    },
                    defaultValue = 10f,
                    valueDisplayTransform = { "%.0f".format(it) }
                )
                AdjustmentSlider(
                    label = "Threshold",
                    value = params.halationThreshold,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateHalation(
                            params.halationIntensity, it,
                            params.halationSpread, params.halationRedBias
                        )
                    },
                    defaultValue = 0.8f
                )
                AdjustmentSlider(
                    label = "Red Bias",
                    value = params.halationRedBias,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateHalation(
                            params.halationIntensity, params.halationThreshold,
                            params.halationSpread, it
                        )
                    },
                    defaultValue = 0.7f
                )
            }
        }

        // ── Sharpen ───────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sharpen",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { viewModel.updateSharpen(0f) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Sharpen",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = "Amount",
                    value = params.sharpenAmount,
                    range = 0f..2f,
                    onValueChange = { viewModel.updateSharpen(it) },
                    defaultValue = 0f
                )
            }
        }

        // ── Clarity ───────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Clarity",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { viewModel.updateClarity(0f) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Clarity",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = "Amount",
                    value = params.clarityAmount,
                    range = -1f..1f,
                    onValueChange = { viewModel.updateClarity(it, params.clarityRadius) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = "Radius",
                    value = params.clarityRadius,
                    range = 1f..50f,
                    onValueChange = { viewModel.updateClarity(params.clarityAmount, it) },
                    defaultValue = 15f,
                    valueDisplayTransform = { "%.0f".format(it) }
                )
            }
        }

        // ── Vignette ──────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Vignette",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            viewModel.updateParams(params.copy(lensVignetteStrength = 0f))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset Vignette",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = "Strength",
                    value = params.lensVignetteStrength,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(lensVignetteStrength = it))
                    },
                    defaultValue = 0f
                )
            }
        }

        // ── LUT ───────────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LUT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.updateLut(false, "")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reset LUT",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = params.lutEnabled,
                            onCheckedChange = { viewModel.updateLut(it, params.lutPath) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
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
                                    viewModel.updateLut(true, lutName)
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
