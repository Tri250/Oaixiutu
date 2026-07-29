package com.alcedo.studio.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

@Composable
fun SettingsScreen(
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onAgreement: () -> Unit,
    onManageSpace: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = Strings.res
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val languageManager = remember { LanguageManager() }
    val currentLanguage by languageManager.language.collectAsState(initial = Strings.language)
    val appSettings = state.appSettings

    BackHandler { onBack() }

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
            // Language
            Text(s.language, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                Language.entries.forEach { lang ->
                    FilterChip(
                        selected = lang == currentLanguage,
                        onClick = { scope.launch { languageManager.setLanguage(lang) } },
                        label = { Text(lang.displayName, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
            HorizontalDivider(color = AlcedoColors.Divider)
            // Theme
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.theme, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    listOf(s.themeDark, s.themeLight, s.themeSystem).forEach { theme ->
                        FilterChip(
                            selected = appSettings.theme == theme,
                            onClick = { viewModel.setTheme(theme) },
                            label = { Text(theme, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }
            }
            HorizontalDivider(color = AlcedoColors.Divider)
            // Default view
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.defaultView, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    listOf(s.gridView, s.listView).forEach { view ->
                        FilterChip(
                            selected = appSettings.defaultView == view,
                            onClick = { viewModel.setDefaultView(view) },
                            label = { Text(view, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }
            }
        }

        // ---- Editor ----
        SettingsSection(title = s.tabEditor) {
            SettingsRow(label = s.showHistogram, value = "")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.autoSaveInterval, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(s.autoSaveIntervalValue, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary)
            }
            HorizontalDivider(color = AlcedoColors.Divider)
            // GPU Backend
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.gpuBackend, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs)) {
                    listOf(s.vulkan, s.cpu).forEach { backend ->
                        FilterChip(
                            selected = appSettings.gpuBackend == backend,
                            onClick = { viewModel.setGpuBackend(backend) },
                            label = { Text(backend, style = MaterialTheme.typography.bodySmall) },
                        )
                    }
                }
            }
        }

        // ---- AI ----
        SettingsSection(title = s.settingsAi) {
            OutlinedTextField(
                value = appSettings.aiApiKey,
                onValueChange = { viewModel.setApiKey(it) },
                label = { Text(s.apiKey) },
                placeholder = { Text("sk-...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = appSettings.aiEndpoint,
                onValueChange = { viewModel.setAiEndpoint(it) },
                label = { Text(s.endpoint) },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = appSettings.aiModel,
                onValueChange = { viewModel.setAiModel(it) },
                label = { Text(s.model) },
                placeholder = { Text("gpt-4o") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(color = AlcedoColors.Divider)
            // Default strictness
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.defaultStrictness, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextSecondary, modifier = Modifier.weight(1f))
                Text("${"%.0f%%".format(appSettings.aiStrictness * 100)}", style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary)
            }
            Slider(
                value = appSettings.aiStrictness,
                onValueChange = { viewModel.setAiStrictness(it) },
                colors = SliderDefaults.colors(
                    thumbColor = AlcedoColors.AccentBlue,
                    activeTrackColor = AlcedoColors.AccentBlue,
                ),
            )
            HorizontalDivider(color = AlcedoColors.Divider)
            val privacy = state.privacy
            ToggleRow(s.cloudLlm, s.cloudLlmDesc, privacy?.cloudLlmAllowed == true) { viewModel.setCloudLlmAllowed(it) }
            ToggleRow(s.onDeviceAi, s.onDeviceAiDesc, privacy?.onDeviceAiAllowed == true) { viewModel.setOnDeviceAiAllowed(it) }
        }

        // ---- Storage ----
        SettingsSection(title = s.settingsStorage) {
            SettingsRow(label = s.cacheSize, value = Project.formatBytes(state.cacheSizeBytes))
            ActionRow(label = s.clearCache, isLoading = state.isClearingCache, onClick = { viewModel.clearCache() })
            ActionRow(label = s.sweepOrphans, isLoading = state.isSweeping, onClick = { viewModel.sweepOrphans() })
            ActionRow(label = s.manageSpace, onClick = onManageSpace)
        }

        // ---- Privacy ----
        SettingsSection(title = s.privacy) {
            ToggleRow(s.analytics, s.analyticsDesc, privacy = state.privacy, getter = { it.telemetryAllowed }, setter = { viewModel.setTelemetryAllowed(it) })
            ToggleRow(s.crashReports, s.crashReportsDesc, privacy = state.privacy, getter = { it.crashReportEnabled }, setter = { viewModel.setCrashReportEnabled(it) })
        }

        // ---- Diagnostics ----
        SettingsSection(title = s.diagnostics) {
            SettingsRow(label = s.nativeVersion, value = state.nativeVersion)
            SettingsRow(label = s.gpuAvailable, value = if (state.gpuAvailable) s.available else s.unavailable)
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
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = AlcedoColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
private fun <T> ToggleRow(
    label: String,
    description: String,
    privacy: T?,
    getter: (T) -> Boolean,
    setter: (Boolean) -> Unit,
) {
    val checked = privacy?.let { getter(it) } ?: false
    ToggleRow(label, description, checked, setter)
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
