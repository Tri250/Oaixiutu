package com.alcedo.studio.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    val scroll = rememberScrollState()
    val uriHandler = LocalUriHandler.current

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
                .padding(DesignTokens.spacingXxl),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(AlcedoColors.AccentBlue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "A",
                    style = MaterialTheme.typography.headlineLarge,
                    color = AlcedoColors.TextOnAccent,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = s.appName,
                style = MaterialTheme.typography.headlineMedium,
                color = AlcedoColors.TextPrimary,
            )
            Text(
                text = "Professional RAW Photo Editor",
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextTertiary,
            )

            HorizontalDivider(color = AlcedoColors.Divider)

            // Version info
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                InfoRow(s.version, appVersion())
                InfoRow("Version Code", appVersionCode())
                InfoRow("Build Info", "Release · ${com.alcedo.studio.util.ContextProvider.requireContext().packageName}")
                InfoRow(s.license, "GPL-3.0")
            }

            HorizontalDivider(color = AlcedoColors.Divider)

            // Credits
            Text(
                text = s.credits,
                style = MaterialTheme.typography.titleMedium,
                color = AlcedoColors.TextSecondary,
                modifier = Modifier.align(Alignment.Start),
            )
            Text(
                text = "Alcedo Studio — Professional RAW Photo Editor.\n" +
                    "Built on a Vulkan/NDK non-destructive pipeline.\n" +
                    "Powered by ONNX Runtime for on-device AI.",
                style = MaterialTheme.typography.bodyMedium,
                color = AlcedoColors.TextTertiary,
                modifier = Modifier.align(Alignment.Start),
            )

            HorizontalDivider(color = AlcedoColors.Divider)

            // Links
            Text(
                text = "Links",
                style = MaterialTheme.typography.titleMedium,
                color = AlcedoColors.TextSecondary,
                modifier = Modifier.align(Alignment.Start),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
            ) {
                OutlinedButton(onClick = { runCatching { uriHandler.openUri("https://github.com/alcedo-studio") } }) {
                    Icon(Icons.Outlined.Code, contentDescription = null, tint = AlcedoColors.AccentBlue, modifier = Modifier.size(18.dp))
                    Text("GitHub", color = AlcedoColors.AccentBlue)
                }
                OutlinedButton(onClick = { runCatching { uriHandler.openUri("https://alcedo.studio") } }) {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = AlcedoColors.AccentBlue, modifier = Modifier.size(18.dp))
                    Text("Website", color = AlcedoColors.AccentBlue)
                }
            }

            HorizontalDivider(color = AlcedoColors.Divider)

            // Third-party licenses
            Text(
                text = "Third-Party Licenses",
                style = MaterialTheme.typography.titleMedium,
                color = AlcedoColors.TextSecondary,
                modifier = Modifier.align(Alignment.Start),
            )
            val licenses = listOf(
                "ONNX Runtime" to "MIT License",
                "LibRaw" to "LGPL-2.1",
                "Vulkan" to "Apache-2.0",
                "Material Design 3" to "Apache-2.0",
                "Hilt" to "Apache-2.0",
                "Kotlin Coroutines" to "Apache-2.0",
                "Compose UI" to "Apache-2.0",
                "DataStore" to "Apache-2.0",
            )
            licenses.forEach { (name, license) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = MaterialTheme.typography.bodySmall, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
                    Text(license, style = MaterialTheme.typography.labelSmall, color = AlcedoColors.TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
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

private fun appVersionCode(): String {
    return runCatching {
        val ctx = com.alcedo.studio.util.ContextProvider.requireContext()
        val pkg = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        pkg.longVersionCode.toString()
    }.getOrDefault("1")
}
