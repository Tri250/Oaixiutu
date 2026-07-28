package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Waveform scope analyzer composable wrapping [WaveformView] with mode controls.
 * Supports switching between combined waveform and RGB parade modes.
 */
@Composable
fun WaveformScope(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    paradeMode: Boolean = true,
    onParadeModeChange: (Boolean) -> Unit = {},
) {
    Box(
        modifier = modifier
            .background(AlcedoColors.Obsidian)
            .padding(DesignTokens.spacingXs),
    ) {
        WaveformView(
            bitmap = bitmap,
            paradeMode = paradeMode,
            modifier = Modifier.fillMaxSize(),
        )
        // Mode toggle label
        if (paradeMode) {
            androidx.compose.material3.Text(
                text = "RGB Parade",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = AlcedoColors.TextTertiary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DesignTokens.spacingXs),
            )
        }
    }
}
