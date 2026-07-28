package com.alcedo.studio.ui.ai

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.domain.service.SemanticGenerationService
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens
import kotlinx.coroutines.launch

/**
 * Modal dialog that generates semantic tags/caption for an image via
 * [SemanticGenerationService] and displays the result. Used from the album
 * context menu and the AI tools to enrich an image's metadata.
 */
@Composable
fun SemanticGenerationDialog(
    uri: Uri,
    imageId: String,
    service: SemanticGenerationService,
    onDismiss: () -> Unit,
    onApplied: (AiImageAnalysis?) -> Unit = {},
) {
    val s = Strings.res
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(true) }
    var analysis by remember { mutableStateOf<AiImageAnalysis?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri, imageId) {
        scope.launch {
            runCatching { service.analyze(uri, imageId) }
                .onSuccess { result -> analysis = result; isGenerating = false }
                .onFailure { e -> error = e.message; isGenerating = false }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.generatingTags) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(color = AlcedoColors.AccentBlue)
                        Text(s.generatingTags, color = AlcedoColors.TextSecondary)
                    }
                } else if (error != null) {
                    Text(error ?: s.error, color = AlcedoColors.Danger)
                } else {
                    analysis?.let { a ->
                        Text(a.caption, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary)
                        if (a.tags.isNotEmpty()) {
                            Text(
                                text = a.tags.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = AlcedoColors.AccentBlue,
                                modifier = Modifier.padding(top = DesignTokens.spacingXs),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApplied(analysis)
                    onDismiss()
                },
                enabled = !isGenerating,
            ) {
                Text(s.done, color = AlcedoColors.AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(s.cancel, color = AlcedoColors.TextSecondary)
            }
        },
    )
}
