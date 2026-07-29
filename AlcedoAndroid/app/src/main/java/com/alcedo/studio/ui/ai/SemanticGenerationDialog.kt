package com.alcedo.studio.ui.ai

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Semantic tag-generation dialog. Owns no state itself — the
 * [SemanticGenerationViewModel] drives generation progress and the resulting
 * [AiImageAnalysis]. Shown over the editor/album to caption + tag an image.
 */
@Composable
fun SemanticGenerationDialog(
    uri: Uri,
    imageId: String,
    onDismiss: () -> Unit,
    onApplied: (AiImageAnalysis?) -> Unit = {},
    viewModel: SemanticGenerationViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = {
            viewModel.cancelScan()
            onDismiss()
        },
        title = { Text(s.generateTags) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
                // Folder selection
                OutlinedTextField(
                    value = state.selectedFolder,
                    onValueChange = viewModel::setSelectedFolder,
                    label = { Text(s.folderToScan) },
                    placeholder = { Text(s.defaultScanFolder) },
                    singleLine = true,
                    enabled = !state.isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Model selection
                Text(
                    text = s.model,
                    style = MaterialTheme.typography.labelMedium,
                    color = AlcedoColors.TextTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    listOf("MobileCLIP2", "SigLIP2", "Jina CLIP v2").forEach { model ->
                        FilterChip(
                            selected = state.selectedModel == model,
                            onClick = { viewModel.setSelectedModel(model) },
                            enabled = !state.isGenerating,
                            label = { Text(model, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }

                // Progress with ETA
                if (state.isGenerating) {
                    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AlcedoColors.AccentBlue,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${state.completedCount} / ${state.totalCount.coerceAtLeast(1)} images",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlcedoColors.TextSecondary,
                            )
                            val pct = (state.progress * 100f).toInt().coerceIn(0, 100)
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlcedoColors.TextTertiary,
                            )
                        }
                    }
                }

                // Error display
                state.error?.let { err ->
                    Text(err, color = AlcedoColors.Danger, style = MaterialTheme.typography.bodySmall)
                }

                // Analysis result
                state.analysis?.let { a ->
                    Text(
                        text = s.result,
                        style = MaterialTheme.typography.labelMedium,
                        color = AlcedoColors.TextTertiary,
                        modifier = Modifier.padding(top = DesignTokens.spacingSm),
                    )
                    Text(a.caption, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary)
                    if (a.tags.isNotEmpty()) {
                        Text(
                            text = a.tags.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = AlcedoColors.AccentBlue,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.isGenerating -> {
                    TextButton(onClick = viewModel::cancelScan) {
                        Text(s.stop, color = AlcedoColors.Danger)
                    }
                }
                state.analysis != null -> {
                    TextButton(
                        onClick = { onApplied(state.analysis); viewModel.cancelScan(); onDismiss() },
                    ) {
                        Text(s.done, color = AlcedoColors.AccentBlue)
                    }
                }
                else -> {
                    Button(
                        onClick = { viewModel.startScan(uri, imageId) },
                        enabled = !state.isGenerating,
                    ) {
                        Text(s.startScan)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancelScan(); onDismiss() }, enabled = !state.isGenerating) {
                Text(s.cancel, color = AlcedoColors.TextSecondary)
            }
        },
    )
}
