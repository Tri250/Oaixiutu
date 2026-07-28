package com.alcedo.studio.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alcedo.studio.data.model.Project
import com.alcedo.studio.i18n.Language
import com.alcedo.studio.i18n.LanguageManager
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens
import kotlinx.coroutines.launch

/**
 * Settings screen. Sections: General (language), AI/Privacy (cloud LLM,
 * on-device AI, telemetry toggles), Storage (cache size, clear cache, sweep,
 * manage space), Diagnostics (native version, GPU), Presets (restore defaults),
 * and About (links to about/privacy/agreement pages).
 */
@Composable
fun SettingsScreen(
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onAgreement: () -> Unit,
    onManageSpace: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val languageManager = remember { LanguageManager() }
    val currentLanguage by languageManager.language.collectAsState(initial = Strings.language)
    var languageExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(currentLanguage) {
        Strings.setLanguage(currentLanguage)
    }

    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AlcedoColors.SurfaceBase)
            .verticalScroll(scroll)
            .padding(DesignTokens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingLg),
    ) {
        // ---- General ----
        SettingsSection(title = s.settingsGeneral) {
            SettingsRow(
                label = s.language,
                value = currentLanguage.displayName,
            )
            Language.entries.forEach { lang ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.spacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = lang.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (lang == currentLanguage) AlcedoColors.AccentBlue else AlcedoColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (lang == currentLanguage) {
                        Text("✓", color = AlcedoColors.AccentBlue)
                    } else {
                        TextButton(onClick = {
                            scope.launch { languageManager.setLanguage(lang) }
                        }) { Text(s.save, color = AlcedoColors.AccentBlue) }
                    }
                }
            }
        }

        // ---- AI / Privacy ----
        SettingsSection(title = s.settingsAi) {
            val privacy = state.privacy
            ToggleRow(
                label = s.cloudLlm,
                description = s.cloudLlmDesc,
                checked = privacy?.cloudLlmAllowed == true,
                onCheckedChange = { viewModel.setCloudLlmAllowed(it) },
            )
            ToggleRow(
                label = s.onDeviceAi,
                description = s.onDeviceAiDesc,
                checked = privacy?.onDeviceAiAllowed == true,
                onCheckedChange = { viewModel.setOnDeviceAiAllowed(it) },
            )
            ToggleRow(
                label = s.telemetry,
                description = s.telemetryDesc,
                checked = privacy?.telemetryAllowed == true,
                onCheckedChange = { viewModel.setTelemetryAllowed(it) },
            )
        }

        // ---- Storage ----
        SettingsSection(title = s.settingsStorage) {
            SettingsRow(label = s.cacheSize, value = Project.formatBytes(state.cacheSizeBytes))
            ActionRow(
                label = s.clearCache,
                isLoading = state.isClearingCache,
                onClick = { viewModel.clearCache() },
            )
            ActionRow(label = s.sweepOrphans, onClick = { viewModel.sweepOrphans() })
            ActionRow(label = s.manageSpace, onClick = onManageSpace)
        }

        // ---- Diagnostics ----
        SettingsSection(title = s.nativeVersion) {
            SettingsRow(label = s.nativeVersion, value = state.nativeVersion)
            SettingsRow(
                label = s.gpuAvailable,
                value = if (state.gpuAvailable) s.available else s.unavailable,
            )
        }

        // ---- Presets ----
        SettingsSection(title = s.presets) {
            ActionRow(
                label = s.restorePresets,
                isLoading = state.isRestoringPresets,
                onClick = { viewModel.restoreBuiltInPresets() },
            )
        }

        // ---- About ----
        SettingsSection(title = s.settingsAbout) {
            NavRow(label = s.about, onClick = onAbout)
            HorizontalDivider(color = AlcedoColors.Divider)
            NavRow(label = s.privacyPolicy, onClick = onPrivacy)
            HorizontalDivider(color = AlcedoColors.Divider)
            NavRow(label = s.userAgreement, onClick = onAgreement)
        }

        state.message?.let { msg ->
            Text(text = msg, color = AlcedoColors.Success, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceRaised),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(DesignTokens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = AlcedoColors.TextTertiary,
            )
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = AlcedoColors.TextTertiary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AlcedoColors.AccentBlue)
        } else {
            TextButton(onClick = onClick) { Text(label, color = AlcedoColors.AccentBlue) }
        }
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = AlcedoColors.TextTertiary)
        }
    }
}
