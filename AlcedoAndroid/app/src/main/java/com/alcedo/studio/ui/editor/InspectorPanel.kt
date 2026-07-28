package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoMonoStyle
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Inspector panel showing summary metadata for the active image:
 * thumbnail, filename, dimensions, file size, camera, lens, exposure
 * settings, rating, AI tags, and edit history summary.
 * Used as a collapsible side panel in the editor.
 */
@Composable
fun InspectorPanel(
    image: ImageItem?,
    exif: Map<String, String>,
    editCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    if (image == null) {
        Column(modifier = modifier.padding(DesignTokens.spacingLg)) {
            Text(text = "—", color = AlcedoColors.TextTertiary)
        }
        return
    }

    Column(
        modifier = modifier.padding(DesignTokens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        SectionHeader(title = s.inspector)

        // Filename + dimensions
        InspectorRow(s.dimensions, "${image.width}×${image.height}")
        InspectorRow(s.camera, image.cameraModel ?: "—")
        InspectorRow(s.lens, image.lensModel ?: "—")

        // Exposure summary
        val exposureLine = buildString {
            image.aperture?.let { append("f/$it") }
            image.shutterSpeed?.let { if (isNotEmpty()) append(" · "); append(it) }
            image.iso?.let { if (isNotEmpty()) append(" · "); append("ISO $it") }
            image.focalLength?.let { if (isNotEmpty()) append(" · "); append("${it}mm") }
        }
        if (exposureLine.isNotEmpty()) {
            InspectorRow(s.exposure, exposureLine)
        }

        // File info
        InspectorRow("File", image.displayName)
        InspectorRow("Size", com.alcedo.studio.data.model.Project.formatBytes(image.fileSizeBytes))
        InspectorRow("Type", if (image.isRaw) "RAW" else image.fileExtension.uppercase())

        // Rating
        SectionHeader(title = s.rate)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Text(s.rate, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
            repeat(5) { i ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            if (i < image.rating) AlcedoColors.StarOn else AlcedoColors.SurfaceElevated,
                            CircleShape,
                        ),
                )
            }
        }

        // AI tags
        if (image.aiTags.isNotEmpty()) {
            SectionHeader(title = s.aiSearch)
            image.aiTags.forEach { tag ->
                Text(
                    text = "· $tag",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlcedoColors.TextSecondary,
                )
            }
        }

        // Edit history summary
        if (editCount > 0) {
            SectionHeader(title = s.history)
            Text(
                text = "$editCount edits",
                style = MaterialTheme.typography.bodySmall,
                color = AlcedoColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = AlcedoMonoStyle,
            color = AlcedoColors.TextPrimary,
            modifier = Modifier.weight(2f),
            maxLines = 2,
        )
    }
}
