package com.alcedo.studio.ui.ai

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.data.model.AiModelAsset
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.domain.service.SemanticGenerationService
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens
import kotlinx.coroutines.launch

private data class TagPreview(
    val imageName: String,
    val tags: List<String>,
)

@Composable
fun SemanticGenerationDialog(
    uri: Uri,
    imageId: String,
    service: SemanticGenerationService,
    onDismiss: () -> Unit,
    onApplied: (AiImageAnalysis?) -> Unit = {},
) {
    val s = Strings.res
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var analysis by remember { mutableStateOf<AiImageAnalysis?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("MobileCLIP2") }
    var completedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(10) }
    var isScanning by remember { mutableStateOf(false) }

    // Tag previews
    val tagPreviews = remember {
        listOf(
            TagPreview("IMG_001.CR3", listOf("landscape", "sunset", "mountain")),
            TagPreview("IMG_002.CR3", listOf("portrait", "indoor", "studio")),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.generateTags) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
                // Folder selection
                OutlinedTextField(
                    value = selectedFolder,
                    onValueChange = { selectedFolder = it },
                    label = { Text("Folder to scan") },
                    placeholder = { Text("/sdcard/DCIM/Camera") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Model selection
                Text(
                    text = "Model",
                    style = MaterialTheme.typography.labelMedium,
                    color = AlcedoColors.TextTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    listOf("MobileCLIP2", "SigLIP2", "Jina CLIP v2").forEach { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model },
                            label = { Text(model, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }

                // Progress with ETA
                if (isScanning || isGenerating) {
                    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AlcedoColors.AccentBlue,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "$completedCount / $totalCount images",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlcedoColors.TextSecondary,
                            )
                            val etaSeconds = ((totalCount - completedCount) * 2)
                            Text(
                                text = "ETA: ~${etaSeconds}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = AlcedoColors.TextTertiary,
                            )
                        }
                    }
                }

                // Error display
                if (error != null) {
                    Text(error ?: s.error, color = AlcedoColors.Danger, style = MaterialTheme.typography.bodySmall)
                }

                // Preview of generated tags
                if (tagPreviews.isNotEmpty()) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlcedoColors.TextTertiary,
                        modifier = Modifier.padding(top = DesignTokens.spacingSm),
                    )
                    tagPreviews.forEach { preview ->
                        Column {
                            Text(
                                text = preview.imageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlcedoColors.TextPrimary,
                            )
                            Text(
                                text = preview.tags.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = AlcedoColors.AccentBlue,
                            )
                        }
                    }
                }

                // Analysis result
                analysis?.let { a ->
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
            if (isScanning || isGenerating) {
                TextButton(onClick = {
                    isScanning = false
                    isGenerating = false
                }) {
                    Text("Stop", color = AlcedoColors.Danger)
                }
            } else if (analysis != null) {
                TextButton(
                    onClick = { onApplied(analysis); onDismiss() },
                ) {
                    Text(s.done, color = AlcedoColors.AccentBlue)
                }
            } else {
                Button(onClick = {
                    isScanning = true
                    isGenerating = true
                    scope.launch {
                        runCatching { service.analyze(uri, imageId) }
                            .onSuccess { result -> analysis = result; isGenerating = false; isScanning = false }
                            .onFailure { e -> error = e.message; isGenerating = false; isScanning = false }
                    }
                }) {
                    Text("Start Scan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isGenerating) {
                Text(s.cancel, color = AlcedoColors.TextSecondary)
            }
        },
    )
}
