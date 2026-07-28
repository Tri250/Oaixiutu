package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Image inspector panel. Read-only summary of the open image: file metadata,
 * camera/lens EXIF, rating/flag/label and AI tags. Used from the editor's
 * overflow "Inspector" entry.
 */
@Composable
fun ImageInspectorPanel(
    image: ImageItem?,
    exif: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    if (image == null) {
        Text(text = "—", color = AlcedoColors.TextTertiary)
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.inspector)
        InspectorRow(s.dimensions, "${image.width}×${image.height}")
        InspectorRow(s.camera, listOfNotNull(image.cameraModel, exif["Make"]).joinToString(" "))
        InspectorRow(s.lens, image.lensModel ?: exif["LensModel"] ?: "—")
        InspectorRow(s.focalLength, image.focalLength?.let { "${it}mm" } ?: "—")
        InspectorRow(s.aperture, image.aperture?.let { "f/${it}" } ?: "—")
        InspectorRow(s.shutterSpeed, image.shutterSpeed ?: "—")
        InspectorRow(s.iso, image.iso?.toString() ?: "—")

        SectionHeader(title = s.rate)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        ) {
            Text(s.rate, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
            repeat(5) { i ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (i < image.rating) AlcedoColors.StarOn else AlcedoColors.SurfaceElevated,
                            CircleShape,
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        ) {
            Text(s.flag, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
            val flagColor = when (image.flag) {
                ImageFlag.PICK -> AlcedoColors.FlagPick
                ImageFlag.REJECT -> AlcedoColors.FlagReject
                ImageFlag.NONE -> AlcedoColors.TextDisabled
            }
            Text(image.flag.name, color = flagColor)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        ) {
            Text(s.colorLabel, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
            if (image.colorLabel != ColorLabel.NONE) {
                Box(modifier = Modifier.size(12.dp).background(ColorLabelToCompose(image.colorLabel), CircleShape))
            }
            Text(image.colorLabel.name, color = AlcedoColors.TextTertiary)
        }

        if (image.aiTags.isNotEmpty()) {
            SectionHeader(title = s.aiSearch)
            image.aiTags.forEach { tag ->
                Text(text = "· $tag", style = MaterialTheme.typography.bodySmall, color = AlcedoColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextTertiary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary, modifier = Modifier.weight(2f), maxLines = 2)
    }
}

private fun ColorLabelToCompose(label: ColorLabel): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(label.hex)
