package com.alcedo.studio.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyConsentDialog(
    onDismiss: () -> Unit,
    onAccept: (PrivacyManager.ConsentStatus) -> Unit
) {
    var analyticsConsent by remember { mutableStateOf(false) }
    var crashReportsConsent by remember { mutableStateOf(false) }
    var aiProcessingConsent by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { /* Cannot dismiss - must make a choice */ },
        title = {
            Text("Privacy & Data Preferences")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Alcedo Studio respects your privacy. Choose which features to enable:",
                    style = MaterialTheme.typography.bodyMedium
                )

                // AI Processing
                ConsentItem(
                    title = "On-Device AI Processing",
                    description = "Enable CLIP-based image search and AI-powered editing suggestions. All processing happens locally on your device.",
                    checked = aiProcessingConsent,
                    onCheckedChange = { aiProcessingConsent = it },
                    required = false
                )

                // Crash Reports
                ConsentItem(
                    title = "Crash Reports",
                    description = "Help us improve by sending anonymous crash reports. No personal data is included.",
                    checked = crashReportsConsent,
                    onCheckedChange = { crashReportsConsent = it },
                    required = false
                )

                // Analytics
                ConsentItem(
                    title = "Usage Analytics",
                    description = "Anonymous usage statistics to understand how features are used. No personal data is collected.",
                    checked = analyticsConsent,
                    onCheckedChange = { analyticsConsent = it },
                    required = false
                )

                HorizontalDivider()

                Text(
                    "You can change these preferences at any time in Settings > Privacy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "Your photos are processed locally and never uploaded to our servers. AI API keys (if you provide them) are stored encrypted on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (analyticsConsent) PrivacyManager.grantConsent(PrivacyManager.ConsentType.ANALYTICS)
                else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.ANALYTICS)
                if (crashReportsConsent) PrivacyManager.grantConsent(PrivacyManager.ConsentType.CRASH_REPORTS)
                else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.CRASH_REPORTS)
                if (aiProcessingConsent) PrivacyManager.grantConsent(PrivacyManager.ConsentType.AI_PROCESSING)
                else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.AI_PROCESSING)
                PrivacyManager.markFirstLaunchComplete()
                onAccept(PrivacyManager.getConsentStatus())
            }) {
                Text("Save Preferences")
            }
        }
    )
}

@Composable
private fun ConsentItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    required: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (required) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("(Required)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
