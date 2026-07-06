package com.alcedo.studio.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.i18n.stringRes

/**
 * Scrollable Privacy Policy screen.
 *
 * Renders the full Chinese privacy policy text (suitable for Chinese app store
 * review) and, in first-launch consent mode, presents Agree / Disagree actions.
 * - Agree: marks first-launch consent complete via [PrivacyManager] and pops
 *   the back stack.
 * - Disagree: finishes the host Activity so the user exits the app, matching
 *   Chinese app store requirements that disagreeing to the privacy policy
 *   must not allow continued use.
 *
 * When opened from Settings ([showConsentActions] = false) the bottom action
 * bar is hidden and only the top-bar back button is offered for navigation.
 *
 * @param navController navigation controller for the back action
 * @param showConsentActions whether to render the Agree / Disagree actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    navController: NavController,
    showConsentActions: Boolean = false
) {
    val context = LocalContext.current
    val s = Strings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringRes { privacyPolicyTitle },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringRes { back },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (showConsentActions) {
                PrivacyConsentActionBar(
                    onAgree = {
                        com.alcedo.studio.privacy.PrivacyManager.markFirstLaunchComplete()
                        navController.popBackStack()
                    },
                    onDisagree = {
                        // Exit the app as required by Chinese app stores when the
                        // user declines the privacy policy on first launch.
                        (context as? Activity)?.finishAffinity()
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = s.privacyPolicyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = s.privacyEffectiveDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Policy body
            Text(
                text = s.privacyContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            // Trailing spacer so the last line clears the bottom action bar.
            Spacer(modifier = Modifier.height(if (showConsentActions) 24.dp else 32.dp))
        }
    }
}

/**
 * Bottom action bar with Agree / Disagree buttons used during first-launch
 * consent. Extracted so it can be shared with [UserAgreementScreen].
 */
@Composable
internal fun PrivacyConsentActionBar(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    val s = Strings.current
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onDisagree,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(s.privacyDisagree)
            }
            Button(
                onClick = onAgree,
                modifier = Modifier.weight(1f)
            ) {
                Text(s.privacyAgree)
            }
        }
    }
}
