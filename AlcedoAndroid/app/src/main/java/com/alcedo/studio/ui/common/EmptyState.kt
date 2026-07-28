package com.alcedo.studio.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * An empty-state placeholder with an icon, title, subtitle and an optional
 * action button. Shown by grids and lists when there is no data to display.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = Icons.Outlined.ImageNotSupported,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(DesignTokens.spacingXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AlcedoColors.TextTertiary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AlcedoColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextTertiary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Button(onClick = onAction) {
                Text(text = actionText)
            }
        }
    }
}
