package com.alcedo.studio.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoMonoStyle
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * An export progress card showing the linear progress, completed/total counts
 * and an estimated time remaining. Used by the export sheet and share panel.
 */
@Composable
fun ExportProgressWithEta(
    completed: Int,
    total: Int,
    etaMs: Long?,
    modifier: Modifier = Modifier,
    label: String = "Exporting",
) {
    val progress = if (total > 0) completed.toFloat() / total else 0f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DesignTokens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$completed / $total",
                style = AlcedoMonoStyle,
                color = AlcedoColors.TextPrimary,
            )
        }
        LinearProgressBar(progress = progress)
        val eta = formatEta(etaMs)
        if (eta.isNotEmpty()) {
            Text(
                text = eta,
                style = MaterialTheme.typography.labelSmall,
                color = AlcedoColors.TextTertiary,
            )
        }
    }
}
