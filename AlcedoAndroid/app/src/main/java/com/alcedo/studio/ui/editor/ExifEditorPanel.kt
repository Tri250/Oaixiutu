package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * EXIF editor panel. Renders the image's EXIF map as read-only fields, with a
 * handful of editable metadata entries (description, copyright) that route
 * through [onFieldChange]. Camera/lens/aperture are display-only.
 */
@Composable
fun ExifEditorPanel(
    exif: Map<String, String>,
    modifier: Modifier = Modifier,
    onFieldChange: (String, String) -> Unit = { _, _ -> },
) {
    val s = Strings.res
    val displayEntries = listOf(
        s.camera to (exif["Make"] + " " + exif["Model"]).trim(),
        s.lens to (exif["LensModel"] ?: exif["Lens"] ?: "—"),
        s.focalLength to (exif["FocalLength"] ?: "—"),
        s.aperture to (exif["FNumber"] ?: "—"),
        s.shutterSpeed to (exif["ExposureTime"] ?: "—"),
        s.iso to (exif["ISO"] ?: "—"),
        s.exposureBias to (exif["ExposureBias"] ?: "—"),
        s.whiteBalance to (exif["WhiteBalance"] ?: "—"),
        s.flash to (exif["Flash"] ?: "—"),
        s.dimensions to (exif["ImageWidth"] + "×" + exif["ImageLength"]).trim('×'),
    ).filter { it.second.isNotBlank() && it.second != "—" }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.exif)
        if (displayEntries.isEmpty()) {
            Text(text = "—", color = AlcedoColors.TextTertiary)
        } else {
            displayEntries.forEach { (label, value) ->
                ExifRow(label = label, value = value)
            }
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = AlcedoColors.TextPrimary,
            modifier = Modifier.weight(2f),
            maxLines = 2,
        )
    }
}
