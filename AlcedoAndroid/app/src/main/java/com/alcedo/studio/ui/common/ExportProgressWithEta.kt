package com.alcedo.studio.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun ExportProgressWithEta(
    progress: Float,
    totalItems: Int = 1,
    completedItems: Int = 0,
    startTimeMs: Long = 0L,
    isIndeterminate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "exportProgress"
    )

    val currentTime = System.currentTimeMillis()
    val elapsedMs = if (startTimeMs > 0) currentTime - startTimeMs else 0L
    val etaMs = if (progress > 0.01f && elapsedMs > 0) {
        (elapsedMs / progress * (1f - progress)).toLong()
    } else 0L

    val etaText = when {
        etaMs <= 0 -> ""
        etaMs < 60_000 -> "~${etaMs / 1000}s remaining"
        etaMs < 3_600_000 -> "~${etaMs / 60_000}m remaining"
        else -> "~${etaMs / 3_600_000}h remaining"
    }

    val progressText = if (totalItems > 1) {
        "$completedItems / $totalItems"
    } else {
        "${(progress * 100).toInt()}%"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = progressText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (etaText.isNotEmpty()) {
                Text(
                    text = etaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.extraSmall),
        )
    }
}
