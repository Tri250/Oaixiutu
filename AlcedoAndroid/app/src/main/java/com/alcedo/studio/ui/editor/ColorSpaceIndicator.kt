package com.alcedo.studio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens
import com.alcedo.studio.ui.theme.PillShape

/**
 * Small overlay badge showing the active output colour space (e.g. sRGB,
 * Display P3) over the editor viewport.
 */
@Composable
fun ColorSpaceIndicator(
    colorSpace: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(AlcedoColors.SurfaceScrim, PillShape)
            .padding(horizontal = DesignTokens.spacingSm, vertical = DesignTokens.spacingXxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = colorSpace,
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextPrimary,
        )
    }
}
