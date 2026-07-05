package com.alcedo.studio.privacy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes

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
            Text(stringRes { privacyTitle })
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringRes { privacyBody },
                    style = MaterialTheme.typography.bodyMedium
                )

                // AI Features
                ConsentItem(
                    title = stringRes { privacyAiFeaturesTitle },
                    description = stringRes { privacyAiFeaturesDesc },
                    checked = aiProcessingConsent,
                    onCheckedChange = { aiProcessingConsent = it },
                    required = false
                )

                // Crash Reports
                ConsentItem(
                    title = stringRes { privacyCrashReportsTitle },
                    description = stringRes { privacyCrashReportsDesc },
                    checked = crashReportsConsent,
                    onCheckedChange = { crashReportsConsent = it },
                    required = false
                )

                // Analytics
                ConsentItem(
                    title = stringRes { privacyAnalyticsTitle },
                    description = stringRes { privacyAnalyticsDesc },
                    checked = analyticsConsent,
                    onCheckedChange = { analyticsConsent = it },
                    required = false
                )

                HorizontalDivider()

                Text(
                    stringRes { privacyChangeNote },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    stringRes { privacyDataNote },
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
                Text(stringRes { privacySavePreferences })
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
                    Text(stringRes { privacyRequired }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
