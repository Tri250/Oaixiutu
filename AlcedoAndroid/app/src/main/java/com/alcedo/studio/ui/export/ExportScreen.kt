package com.alcedo.studio.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.data.model.WatermarkConfig
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.ErrorDialog
import com.alcedo.studio.ui.editor.WatermarkPanel
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Full export screen. Renders the export configuration form (format, quality,
 * resolution, colour space, metadata, Ultra HDR, watermark) and a live progress
 * section while exporting. On completion a [SharePanel] offers share/open
 * actions for the last output.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    imageId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config = state.config
    var showShare by remember { mutableStateOf(false) }
    var formatExpanded by remember { mutableStateOf(false) }
    var csExpanded by remember { mutableStateOf(false) }
    var showWatermark by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(s.exportTitle, color = AlcedoColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AlcedoColors.Charcoal,
                    titleContentColor = AlcedoColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            // ---- Format ----
            Text(s.format, style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
            Box {
                OutlinedTextField(
                    value = "${config.format.name} (.${config.format.extension})",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { formatExpanded = true }) { Text("▾") } },
                )
                DropdownMenu(expanded = formatExpanded, onDismissRequest = { formatExpanded = false }) {
                    ExportFormat.entries.forEach { fmt ->
                        DropdownMenuItem(
                            text = { Text("${fmt.name} (.${fmt.extension})") },
                            onClick = { viewModel.setFormat(fmt); formatExpanded = false },
                        )
                    }
                }
            }

            // ---- Quality (lossy only) ----
            if (config.format == ExportFormat.JPEG || config.format == ExportFormat.WEBP) {
                Text("${s.quality}: ${config.quality}", style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
                Slider(
                    value = config.quality.toFloat(),
                    onValueChange = { viewModel.setQuality(it.toInt()) },
                    valueRange = 1f..100f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = config.ultraHdr,
                        onCheckedChange = { viewModel.setUltraHdr(it) },
                        enabled = config.format == ExportFormat.JPEG,
                    )
                    Text(s.ultraHdr, modifier = Modifier.padding(start = DesignTokens.spacingSm))
                }
            }

            // ---- Max dimension ----
            Text(
                "${s.maxDimension}: ${if (config.maxDimension == 0) "Original" else "${config.maxDimension}px"}",
                style = MaterialTheme.typography.labelMedium,
                color = AlcedoColors.TextTertiary,
            )
            Slider(
                value = config.maxDimension.toFloat(),
                onValueChange = { viewModel.setMaxDimension(it.toInt()) },
                valueRange = 0f..8000f,
            )

            // ---- Colour space ----
            Text(s.colorSpace, style = MaterialTheme.typography.labelMedium, color = AlcedoColors.TextTertiary)
            Box {
                OutlinedTextField(
                    value = config.colorSpace,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { csExpanded = true }) { Text("▾") } },
                )
                DropdownMenu(expanded = csExpanded, onDismissRequest = { csExpanded = false }) {
                    listOf("sRGB", "Display P3", "Rec.2020", "Adobe RGB").forEach { cs ->
                        DropdownMenuItem(
                            text = { Text(cs) },
                            onClick = { viewModel.setColorSpace(cs); csExpanded = false },
                        )
                    }
                }
            }

            // ---- Naming ----
            OutlinedTextField(
                value = config.namingPattern,
                onValueChange = { viewModel.setNamingPattern(it) },
                label = { Text(s.namingPattern) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Metadata + watermark toggles ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = config.includeMetadata,
                    onCheckedChange = { viewModel.setIncludeMetadata(it) },
                )
                Text(s.includeMetadata, modifier = Modifier.padding(start = DesignTokens.spacingSm))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = config.includeWatermark,
                    onCheckedChange = { viewModel.setWatermarkEnabled(it) },
                )
                TextButton(onClick = { showWatermark = !showWatermark }) {
                    Text(s.watermark, color = AlcedoColors.AccentBlue)
                }
            }
            if (showWatermark || config.includeWatermark) {
                WatermarkPanel(
                    config = config.watermark,
                    onConfigChange = { wc -> viewModel.updateWatermark { wc } },
                )
            }

            // ---- Progress / results ----
            if (state.isExporting) {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    Text(
                        "${s.exporting} ${state.completedCount}/${state.totalCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlcedoColors.TextSecondary,
                    )
                    LinearProgressIndicator(
                        progress = { if (state.totalCount > 0) state.completedCount.toFloat() / state.totalCount else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = AlcedoColors.AccentBlue,
                    )
                }
            }

            state.results.forEach { result ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = result.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.success) AlcedoColors.Success else AlcedoColors.Danger,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (result.success) "✓" else "✕",
                        color = if (result.success) AlcedoColors.Success else AlcedoColors.Danger,
                    )
                }
            }

            // ---- Actions ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                if (state.isExporting) {
                    Button(
                        onClick = { viewModel.cancel() },
                        modifier = Modifier.weight(1f),
                    ) { Text(s.exportCancel) }
                } else {
                    Button(
                        onClick = {
                            showShare = false
                            if (imageId != null) viewModel.exportCurrent(imageId)
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(s.exportButton) }
                }
                if (state.lastOutputPath != null && !state.isExporting) {
                    Button(
                        onClick = { showShare = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Text(s.exportComplete)
                    }
                }
            }
        }
    }

    if (showShare && state.lastOutputPath != null) {
        SharePanel(
            outputPath = state.lastOutputPath!!,
            onDismiss = { showShare = false },
        )
    }

    state.error?.let { err ->
        ErrorDialog(title = s.exportFailed, message = err, onDismiss = viewModel::dismissError)
    }
}
