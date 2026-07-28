package com.alcedo.studio.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.BackgroundTaskInfo
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * A bottom-anchored overlay that lists active background tasks (import,
 * export, AI culling, model downloads) with progress and a cancel control.
 * Shown only while [tasks] is non-empty.
 */
@Composable
fun BackgroundTaskBar(
    tasks: List<BackgroundTaskInfo>,
    modifier: Modifier = Modifier,
    onCancel: (String) -> Unit = {},
    onTaskClick: (BackgroundTaskInfo) -> Unit = {},
) {
    AnimatedVisibility(
        visible = tasks.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(AlcedoColors.Graphite)
                .padding(horizontal = DesignTokens.spacingLg, vertical = DesignTokens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            tasks.take(3).forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = DesignTokens.spacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AlcedoColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            val countText = if (task.totalItems > 0) {
                                "  ${task.completedItems}/${task.totalItems}"
                            } else ""
                            Text(
                                text = countText,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlcedoColors.TextTertiary,
                            )
                        }
                        if (task.indeterminate) {
                            IndeterminateProgressBar()
                        } else {
                            LinearProgressBar(progress = task.progress)
                        }
                    }
                    if (task.error != null) {
                        Text(
                            text = "Failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = AlcedoColors.Danger,
                        )
                    } else if (task.cancellable) {
                        IconButton(
                            onClick = { onCancel(task.id) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = "Cancel task",
                                tint = AlcedoColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            if (tasks.size > 3) {
                Text(
                    text = "+${tasks.size - 3} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = AlcedoColors.TextTertiary,
                )
            }
        }
    }
}

/** Formats an ETA in milliseconds to a short human-readable string. */
fun formatEta(etaMs: Long?): String {
    if (etaMs == null || etaMs <= 0) return ""
    val seconds = etaMs / 1000
    return when {
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }
}
