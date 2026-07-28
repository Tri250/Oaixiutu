package com.alcedo.studio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * About page. Shows app name, version, native/GPU build info, licence and
 * credits. Read-only informational screen reached from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    val scroll = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AlcedoColors.SurfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(s.about, color = AlcedoColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AlcedoColors.Charcoal,
                    titleContentColor = AlcedoColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingLg),
        ) {
            Text(
                text = s.appName,
                style = MaterialTheme.typography.headlineMedium,
                color = AlcedoColors.TextPrimary,
            )
            InfoRow(s.version, appVersion())
            InfoRow(s.license, "Alcedo Studio")
            HorizontalDivider(color = AlcedoColors.Divider)
            Text(
                text = s.credits,
                style = MaterialTheme.typography.titleMedium,
                color = AlcedoColors.TextSecondary,
            )
            Text(
                text = "Alcedo Studio — Professional RAW Photo Editor.\nBuilt on a Vulkan/NDK non-destructive pipeline.",
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingMd),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextTertiary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary, modifier = Modifier.weight(2f))
    }
}

private fun appVersion(): String {
    return runCatching {
        val ctx = com.alcedo.studio.util.ContextProvider.requireContext()
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        "${pkg.versionName} (${pkg.longVersionCode})"
    }.getOrDefault("1.0")
}
