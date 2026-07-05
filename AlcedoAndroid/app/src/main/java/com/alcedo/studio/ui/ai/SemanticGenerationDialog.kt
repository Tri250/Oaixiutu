package com.alcedo.studio.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class PersistenceOption(val label: String, val description: String) {
    EMBEDDINGS("Embeddings", "Store vector embeddings for semantic search"),
    LABELS("Labels", "Generate and store AI labels/tags"),
    DESCRIPTIONS("Descriptions", "Generate text descriptions of images"),
    SCORES("Scores", "Generate quality and aesthetic scores")
}

@Composable
fun SemanticGenerationDialog(
    onDismiss: () -> Unit,
    onGenerate: (Set<PersistenceOption>) -> Unit,
    totalImages: Int = 0,
    processedImages: Int = 0,
    isGenerating: Boolean = false,
    progress: Float = 0f,
    currentImageName: String = "",
    results: List<SemanticResultItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    var selectedOptions by remember { mutableStateOf<Set<PersistenceOption>>(PersistenceOption.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Semantic Analysis") },
        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image selection info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Images to analyze",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "$totalImages",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (totalImages == 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Import images first to run semantic analysis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Persistence options
                Text("Persistence Options", style = MaterialTheme.typography.labelLarge)
                PersistenceOption.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = option in selectedOptions,
                            onCheckedChange = {
                                selectedOptions = if (it) selectedOptions + option else selectedOptions - option
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Progress display
                if (isGenerating) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Processing...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "$processedImages / $totalImages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (currentImageName.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Current: $currentImageName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Results preview
                if (results.isNotEmpty()) {
                    Text("Results Preview", style = MaterialTheme.typography.labelLarge)
                    results.take(5).forEach { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    result.imageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (result.labels.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        result.labels.take(3).forEach { label ->
                                            Surface(
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    label,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                                if (result.score > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Score: ${"%.1f".format(result.score * 100)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (results.size > 5) {
                        Text(
                            "... and ${results.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(selectedOptions.toSet()) },
                enabled = selectedOptions.isNotEmpty() && !isGenerating && totalImages > 0
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isGenerating) "Generating..." else "Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isGenerating) { Text("Cancel") }
        }
    )
}

data class SemanticResultItem(
    val imageId: Long,
    val imageName: String,
    val labels: List<String> = emptyList(),
    val description: String = "",
    val score: Float = 0f
)
