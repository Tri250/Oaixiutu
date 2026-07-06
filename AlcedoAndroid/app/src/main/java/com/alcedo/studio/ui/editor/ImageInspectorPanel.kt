package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.LiquidGlassSurface

@Composable
fun ImageInspectorPanel(
    image: ImageModel?,
    modifier: Modifier = Modifier
) {
    if (image == null) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringRes { inspectorNoImage },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val exif = image.exifDisplay

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── File Info ──────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringRes { inspectorFileInfo },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (image.imageName.isNotEmpty()) {
                    InspectorRow("File", image.imageName)
                }
                if (image.fileSize > 0) {
                    InspectorRow("Size", formatFileSize(image.fileSize))
                }
                val dims = exif.imageSize.ifEmpty {
                    if (image.width > 0 && image.height > 0) "${image.width} × ${image.height}" else ""
                }
                if (dims.isNotEmpty()) {
                    InspectorRow("Dimensions", dims)
                }
                if (image.imageType.name.isNotEmpty()) {
                    InspectorRow("Format", image.imageType.name)
                }
            }
        }

        // ── EXIF Data ─────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringRes { inspectorExifData },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                val entries = buildExifEntries(exif)
                if (entries.isEmpty()) {
                    Text(
                        stringRes { inspectorNoExif },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── Rating ─────────────────────────────────────────────────
        val rating = exif.rating.coerceIn(0, 5)
        if (rating > 0) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringRes { inspectorRating },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                if (star <= rating) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = if (star <= rating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildExifEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.cameraMake.isNotEmpty()) entries += "Camera Make" to exif.cameraMake
    if (exif.cameraModel.isNotEmpty()) entries += "Camera Model" to exif.cameraModel
    if (exif.lensModel.isNotEmpty()) entries += "Lens" to exif.lensModel
    if (exif.focalLength.isNotEmpty()) entries += "Focal Length" to exif.focalLength
    if (exif.aperture.isNotEmpty()) entries += "Aperture" to exif.aperture
    if (exif.shutterSpeed.isNotEmpty()) entries += "Shutter Speed" to exif.shutterSpeed
    if (exif.iso.isNotEmpty()) entries += "ISO" to exif.iso
    if (exif.captureDate.isNotEmpty()) entries += "Capture Date" to exif.captureDate
    return entries
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
