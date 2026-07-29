package com.alcedo.studio.privacy

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * First-run privacy consent dialog. Shown before any data processing on first
 * launch. The user must accept to proceed (or decline, which keeps the app in a
 * read-only / on-device-only mode). State is persisted by [PrivacyManager].
 *
 * All user-facing text is sourced from the active [Strings] table so the dialog
 * is localised at runtime.
 */
@Composable
fun PrivacyConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDecline,
        title = { Text(s.privacyConsentTitle, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                s.privacyConsentBody,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(s.accept) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(s.decline) }
        },
    )
}
