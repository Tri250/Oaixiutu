package com.alcedo.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.alcedo.studio.crash.CrashReportService
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.permission.PermissionHelper
import com.alcedo.studio.permission.rememberPermissionState
import com.alcedo.studio.privacy.PrivacyConsentDialog
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.ui.MainScreen
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.ui.theme.DesignTokens
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single-activity Compose host. Installs the system splash, gates the UI on the
 * first-run privacy consent and runtime media permissions, then renders the
 * main navigation host (album/editor/AI/settings).
 *
 * Annotated [AndroidEntryPoint] so Hilt can inject [PrivacyManager].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var privacyManager: PrivacyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash before super.onCreate so the system shows it while
        // the first frame is being prepared.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Kick off the crash reporter in the dedicated :crash process. The
        // service is sticky and self-foregrounds.
        startCrashReporter()

        // Hold the splash until the consent state has been read at least once.
        var consentReady = false
        splash.setKeepOnScreenCondition { !consentReady }

        setContent {
            AlcedoTheme {
                AlcedoHost(
                    privacyManager = privacyManager,
                    onConsentReady = { consentReady = true },
                )
            }
        }
    }

    private fun startCrashReporter() {
        runCatching {
            val intent = android.content.Intent(this, CrashReportService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}

/**
 * Top-level Compose host. Decides between four states:
 *  1. Loading — reading the persisted consent state (bounded by a timeout so
 *     a stalled DataStore read cannot wedge the app on a spinner).
 *  2. Consent gate — first-run privacy dialog shown when consent has not been
 *     recorded yet.
 *  3. Permission gate — runtime media permissions requested.
 *  4. Main content — once consent + permissions are satisfied.
 */
@Composable
private fun AlcedoHost(
    privacyManager: PrivacyManager,
    onConsentReady: () -> Unit,
) {
    // Collect the privacy state into a nullable local so we can distinguish the
    // initial loading state (null) from "consent declined" (a real PrivacyState).
    var consentState: PrivacyManager.PrivacyState? by remember { mutableStateOf(null) }
    // Guard against a stalled DataStore read: if the persisted state is not
    // available within the timeout, fall back to a "not consented" state so the
    // user is not stuck on the loading spinner forever.
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(privacyManager) {
        privacyManager.state.collect { state ->
            consentState = state
            onConsentReady()
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(LOAD_STATE_TIMEOUT_MS)
        if (consentState == null) {
            timedOut = true
            onConsentReady()
        }
    }

    // Compose hooks must be called unconditionally; hoist the permission state
    // to the top so it survives every recomposition regardless of which gate is
    // currently active.
    val permissions = remember { PermissionHelper.requiredMediaPermissions() }
    val permissionState = rememberPermissionState(permissions)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            val state = consentState
            when {
                state == null && !timedOut -> LoadingState()
                state == null || !state.consentGiven -> ConsentGate(privacyManager = privacyManager)
                !permissionState.allGranted -> PermissionGate(
                    allGranted = permissionState.allGranted,
                    onRequest = { permissionState.launcher.launch(permissions.toTypedArray()) },
                )
                else -> MainScreen()
            }
        }
    }
}

/** Loading spinner shown while the persisted privacy state is being read. */
@Composable
private fun LoadingState() {
    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
}

/** First-run privacy consent gate. */
@Composable
private fun ConsentGate(privacyManager: PrivacyManager) {
    val scope = rememberCoroutineScope()
    PrivacyConsentDialog(
        onAccept = {
            scope.launch {
                privacyManager.setConsent(true)
                // Cloud LLM is opt-in after consent; leave it off by default.
                privacyManager.setCloudLlmAllowed(false)
            }
        },
        onDecline = {
            scope.launch { privacyManager.setConsent(false) }
        },
    )
}

/** Runtime permission request gate. */
@Composable
private fun PermissionGate(
    allGranted: Boolean,
    onRequest: () -> Unit,
) {
    val s = Strings.res
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
        modifier = Modifier.padding(DesignTokens.spacingLg),
    ) {
        Text(
            text = s.permissionRationale,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Button(onClick = onRequest, enabled = !allGranted) {
            Text(s.grantAccess)
        }
    }
}

private const val LOAD_STATE_TIMEOUT_MS = 4_000L
