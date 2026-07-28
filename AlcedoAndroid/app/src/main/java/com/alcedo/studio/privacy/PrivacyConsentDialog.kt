package com.alcedo.studio.privacy

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * First-run privacy consent dialog. Shown before any data processing on first
 * launch. The user must accept to proceed (or decline, which keeps the app in a
 * read-only / on-device-only mode). State is persisted by [PrivacyManager].
 */
@Composable
fun PrivacyConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDecline,
        title = { Text("Privacy & Data Use", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                "Alcedo processes your photos on-device for editing and AI features. " +
                    "Optional cloud LLM features (culling, image analysis) send low-resolution " +
                    "thumbnails to a provider of your choice. " +
                    "No photos leave your device unless you enable cloud features.\n\n" +
                    "You can change these choices anytime in Settings > Privacy.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Accept") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Decline") }
        },
    )
}
