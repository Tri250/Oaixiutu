package com.alcedo.studio.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Editor-specific style helpers shared across the editor panels: panel section
 * headers, divider lines and labeled value rows. These keep the dense editor UI
 * visually consistent with the desktop app.
 */
object AlcedoEditorStyle {
    val panelPadding = PaddingValues(
        horizontal = DesignTokens.spacingLg,
        vertical = DesignTokens.spacingMd,
    )

    val sectionSpacing = DesignTokens.spacingLg
    val controlSpacing = DesignTokens.spacingSm
    val panelContentTopPadding = DesignTokens.spacingMd

    val trackColor: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant

    val activeTrackColor: Color
        @Composable get() = MaterialTheme.colorScheme.primary
}

@Composable
fun PanelSectionDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DesignTokens.dividerThickness)
            .background(AlcedoTheme.extendedColors.divider),
    )
}

@Composable
fun PanelSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DesignTokens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AlcedoColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = AlcedoMonoStyle,
            color = AlcedoColors.TextPrimary,
        )
    }
}

@Composable
fun ColorSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, ThumbnailShape)
            .border(DesignTokens.dividerThickness, AlcedoColors.Outline, ThumbnailShape),
    )
}
