package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.ui.common.CollapsibleSection

@Composable
fun ImageInspectorPanel(
    image: ImageModel?,
    modifier: Modifier = Modifier,
    onRate: ((Int) -> Unit)? = null,
    onTag: ((String) -> Unit)? = null,
    aiDescription: String = "",
    aiScore: Float? = null
) {
    if (image == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No image selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // File Info
        CollapsibleSection(title = "File Info") {
            FileInfoRow("Name", image.imageName)
            FileInfoRow("Path", image.imagePath, maxLines = 2)
            FileInfoRow("Size", image.exifDisplay.fileSize.ifEmpty { "${image.fileSize} bytes" })
            FileInfoRow("Dimensions", image.exifDisplay.imageSize.ifEmpty {
                if (image.width > 0 && image.height > 0) "${image.width} x ${image.height}" else ""
            })
            FileInfoRow("Format", image.mimeType.ifEmpty { image.imageType.name })
            FileInfoRow("Type", image.imageType.name)
        }

        HorizontalDivider()

        // EXIF Data
        if (image.hasExif || image.hasExifDisplay) {
            CollapsibleSection(title = "EXIF Data") {
                val exif = image.exifDisplay
                if (exif.cameraMake.isNotEmpty() || exif.cameraModel.isNotEmpty()) {
                    FileInfoRow("Camera", "${exif.cameraMake} ${exif.cameraModel}".trim())
                }
                if (exif.lensModel.isNotEmpty()) {
                    FileInfoRow("Lens", exif.lensModel)
                }
                if (exif.aperture.isNotEmpty()) {
                    FileInfoRow("Aperture", "f/${exif.aperture}")
                }
                if (exif.shutterSpeed.isNotEmpty()) {
                    FileInfoRow("Shutter", exif.shutterSpeed)
                }
                if (exif.iso.isNotEmpty()) {
                    FileInfoRow("ISO", exif.iso)
                }
                if (exif.focalLength.isNotEmpty()) {
                    FileInfoRow("Focal Length", "${exif.focalLength}mm")
                }
                if (exif.captureDate.isNotEmpty()) {
                    FileInfoRow("Captured", exif.captureDate)
                }

                if (exif.cameraMake.isEmpty() && exif.cameraModel.isEmpty() && exif.lensModel.isEmpty()) {
                    Text(
                        "No EXIF data available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()
        }

        // Rating
        CollapsibleSection(title = "Rating") {
            var currentRating by remember { mutableStateOf(0) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(5) { index ->
                    IconButton(
                        onClick = {
                            currentRating = index + 1
                            onRate?.invoke(index + 1)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (index < currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Rate ${index + 1}",
                            modifier = Modifier.size(20.dp),
                            tint = if (index < currentRating) Color(0xFFFFD700)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (currentRating > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        currentRating = 0
                        onRate?.invoke(0)
                    }) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        HorizontalDivider()

        // Tags
        CollapsibleSection(title = "Tags") {
            var tagText by remember { mutableStateOf("") }
            val tags = remember { mutableStateListOf<String>() }

            if (tags.isNotEmpty()) {
                WrapContentRow(
                    items = tags.toList(),
                    onRemove = { tags.remove(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = tagText,
                onValueChange = { tagText = it },
                label = { Text("Add tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (tagText.isNotEmpty()) {
                        IconButton(onClick = {
                            if (tagText.isNotBlank() && tagText !in tags) {
                                tags.add(tagText.trim())
                                onTag?.invoke(tagText.trim())
                            }
                            tagText = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                }
            )
        }

        HorizontalDivider()

        // AI Analysis
        if (aiDescription.isNotEmpty() || aiScore != null) {
            CollapsibleSection(title = "AI Analysis") {
                if (aiDescription.isNotEmpty()) {
                    Text(
                        text = aiDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (aiScore != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quality Score: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "%.1f".format(aiScore * 100),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                aiScore >= 0.8f -> Color(0xFF4CAF50)
                                aiScore >= 0.5f -> Color(0xFFFFA726)
                                else -> Color(0xFFEF5350)
                            }
                        )
                        LinearProgressIndicator(
                            progress = { aiScore },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                                .height(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoRow(label: String, value: String, maxLines: Int = 1) {
    if (value.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.65f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WrapContentRow(
    items: List<String>,
    onRemove: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { tag ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(12.dp)
                    )
                },
                modifier = Modifier.height(28.dp)
            )
        }
    }
}
