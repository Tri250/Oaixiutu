package com.alcedo.studio.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.alcedo.studio.i18n.Language
import com.alcedo.studio.i18n.LanguageManager
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.i18n.strings
import com.alcedo.studio.ui.common.ConfirmDialog
import com.alcedo.studio.ui.theme.AlcedoThemeVariant
import com.alcedo.studio.ui.theme.ThemeManager
import com.alcedo.studio.privacy.PrivacyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val s = Strings.current
    val context = LocalContext.current

    // Theme mode (light/dark/system)
    val darkMode by ThemeManager.darkMode.collectAsState()
    val selectedTheme = when (darkMode) {
        "light" -> s.light
        "dark" -> s.dark
        else -> s.system
    }

    // Theme variant
    val currentVariant by ThemeManager.themeVariant.collectAsState()

    // Language
    val currentLanguage by LanguageManager.currentLanguage.collectAsState()
    val followSystem by LanguageManager.followSystem.collectAsState()
    val selectedLanguage = if (followSystem) s.langSystemDefault else currentLanguage.nativeName

    // Other settings
    var selectedColorScience by remember { mutableStateOf("ACES 2.0") }
    var selectedGpuBackend by remember { mutableStateOf("OpenGL ES") }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearModelsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showThemeVariantDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showColorScienceDialog by remember { mutableStateOf(false) }
    var showGpuDialog by remember { mutableStateOf(false) }
    var showDeleteDataDialog by remember { mutableStateOf(false) }
    var showExportProgressDialog by remember { mutableStateOf(false) }
    val consentStatus by remember { mutableStateOf(PrivacyManager.getConsentStatus()) }
    var analyticsConsent by remember { mutableStateOf(consentStatus.analytics) }
    var crashReportsConsent by remember { mutableStateOf(consentStatus.crashReports) }
    var aiProcessingConsent by remember { mutableStateOf(consentStatus.aiProcessing) }
    val scope = rememberCoroutineScope()
    var exportCachePath by remember { mutableStateOf("/sdcard/Pictures/Alcedo") }
    val cacheSize = remember { "1.2 GB" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes { settingsTitle }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringRes { back })
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("ai_models") }) {
                        Icon(Icons.Default.ModelTraining, contentDescription = stringRes { settingsAiModels })
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance
            SettingsSectionHeader(stringRes { settingsAppearance })

            ListItem(
                headlineContent = { Text(stringRes { settingsTheme }) },
                supportingContent = { Text(selectedTheme) },
                leadingContent = {
                    Icon(
                        if (darkMode == "dark") Icons.Default.DarkMode
                        else if (darkMode == "light") Icons.Default.LightMode
                        else Icons.Default.BrightnessAuto,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringRes { settingsThemeVariant }) },
                supportingContent = { Text(getThemeVariantDisplayName(currentVariant)) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                modifier = Modifier.clickable { showThemeVariantDialog = true }
            )

            HorizontalDivider()

            // Processing
            SettingsSectionHeader(stringRes { settingsProcessing })
            ListItem(
                headlineContent = { Text(stringRes { settingsGpuBackend }) },
                supportingContent = { Text(selectedGpuBackend) },
                leadingContent = { Icon(Icons.Default.Memory, contentDescription = null) },
                modifier = Modifier.clickable { showGpuDialog = true }
            )
            ListItem(
                headlineContent = { Text(stringRes { settingsColorScience }) },
                supportingContent = { Text(selectedColorScience) },
                leadingContent = { Icon(Icons.Default.Science, contentDescription = null) },
                modifier = Modifier.clickable { showColorScienceDialog = true }
            )

            HorizontalDivider()

            // Language
            SettingsSectionHeader(stringRes { settingsLanguage })
            ListItem(
                headlineContent = { Text(stringRes { settingsLanguage }) },
                supportingContent = { Text(selectedLanguage) },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )

            HorizontalDivider()

            // Storage
            SettingsSectionHeader(stringRes { settingsStorage })
            ListItem(
                headlineContent = { Text(stringRes { settingsCacheSize }) },
                supportingContent = { Text(cacheSize) },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                trailingContent = {
                    Row {
                        TextButton(onClick = { showClearCacheDialog = true }) {
                            Text(stringRes { settingsClearThumbnails })
                        }
                        TextButton(onClick = { showClearModelsDialog = true }) {
                            Text(stringRes { settingsClearModels })
                        }
                    }
                }
            )
            OutlinedTextField(
                value = exportCachePath,
                onValueChange = { exportCachePath = it },
                label = { Text(stringRes { settingsExportCachePath }) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
            )

            HorizontalDivider()

            // Privacy
            SettingsSectionHeader("Privacy")
            ListItem(
                headlineContent = { Text("On-Device AI Processing") },
                supportingContent = { Text("CLIP image search & AI suggestions. All processing is local.") },
                leadingContent = { Icon(Icons.Default.Psychology, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = aiProcessingConsent,
                        onCheckedChange = {
                            aiProcessingConsent = it
                            if (it) PrivacyManager.grantConsent(PrivacyManager.ConsentType.AI_PROCESSING)
                            else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.AI_PROCESSING)
                        }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Crash Reports") },
                supportingContent = { Text("Anonymous crash reports. No personal data included.") },
                leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = crashReportsConsent,
                        onCheckedChange = {
                            crashReportsConsent = it
                            if (it) PrivacyManager.grantConsent(PrivacyManager.ConsentType.CRASH_REPORTS)
                            else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.CRASH_REPORTS)
                        }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Usage Analytics") },
                supportingContent = { Text("Anonymous usage statistics. No personal data collected.") },
                leadingContent = { Icon(Icons.Default.Analytics, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = analyticsConsent,
                        onCheckedChange = {
                            analyticsConsent = it
                            if (it) PrivacyManager.grantConsent(PrivacyManager.ConsentType.ANALYTICS)
                            else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.ANALYTICS)
                        }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Export My Data") },
                supportingContent = { Text("Download a copy of your data (right to portability)") },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                modifier = Modifier.clickable { showExportProgressDialog = true }
            )
            ListItem(
                headlineContent = { Text("Delete All Data") },
                supportingContent = { Text("Permanently erase all your data (right to be forgotten)") },
                leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                modifier = Modifier.clickable { showDeleteDataDialog = true }
            )
            ListItem(
                headlineContent = { Text("Data Retention") },
                supportingContent = { Text("Crash reports: 30 days · Temp files: 7 days") },
                leadingContent = { Icon(Icons.Default.Schedule, contentDescription = null) }
            )

            HorizontalDivider()

            // About
            SettingsSectionHeader(stringRes { settingsAbout })
            ListItem(
                headlineContent = { Text(stringRes { settingsAbout }) },
                supportingContent = { Text(stringRes { settingsAboutDesc }) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.clickable { navController.navigate("about") }
            )
            ListItem(
                headlineContent = { Text(stringRes { settingsStatistics }) },
                supportingContent = { Text(stringRes { settingsStatisticsDesc }) },
                leadingContent = { Icon(Icons.Default.BarChart, contentDescription = null) },
                modifier = Modifier.clickable { navController.navigate("stats") }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Theme mode dialog (Light/Dark/System)
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringRes { settingsThemeSelect }) },
            text = {
                Column {
                    listOf("system" to stringRes { system }, "light" to stringRes { light }, "dark" to stringRes { dark }).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setDarkMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = darkMode == mode,
                                onClick = {
                                    ThemeManager.setDarkMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringRes { cancel }) }
            }
        )
    }

    // Theme variant dialog (Gold/Wine/Steel/Graphite/Mist/Dynamic)
    if (showThemeVariantDialog) {
        AlertDialog(
            onDismissRequest = { showThemeVariantDialog = false },
            title = { Text(stringRes { settingsThemeVariantSelect }) },
            text = {
                Column {
                    AlcedoThemeVariant.entries.forEach { variant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemeManager.setThemeVariant(variant)
                                    showThemeVariantDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentVariant == variant,
                                onClick = {
                                    ThemeManager.setThemeVariant(variant)
                                    showThemeVariantDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = when (variant) {
                                    AlcedoThemeVariant.GOLD -> Icons.Default.Star
                                    AlcedoThemeVariant.WINE -> Icons.Default.LocalBar
                                    AlcedoThemeVariant.STEEL -> Icons.Default.Construction
                                    AlcedoThemeVariant.GRAPHITE -> Icons.Default.Brush
                                    AlcedoThemeVariant.MIST -> Icons.Default.Cloud
                                    AlcedoThemeVariant.DYNAMIC -> Icons.Default.AutoAwesome
                                    AlcedoThemeVariant.HASSELBLAD -> Icons.Default.CameraAlt
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(getThemeVariantDisplayName(variant))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeVariantDialog = false }) { Text(stringRes { cancel }) }
            }
        )
    }

    // Language dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringRes { settingsLanguageSelect }) },
            text = {
                Column {
                    // System default
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LanguageManager.setFollowSystem(context)
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = followSystem,
                            onClick = {
                                LanguageManager.setFollowSystem(context)
                                showLanguageDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringRes { langSystemDefault })
                    }

                    HorizontalDivider()

                    Language.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LanguageManager.setLanguage(language)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !followSystem && currentLanguage == language,
                                onClick = {
                                    LanguageManager.setLanguage(language)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(language.nativeName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringRes { cancel }) }
            }
        )
    }

    // Color science dialog
    if (showColorScienceDialog) {
        AlertDialog(
            onDismissRequest = { showColorScienceDialog = false },
            title = { Text(stringRes { settingsColorScience }) },
            text = {
                Column {
                    listOf("ACES 2.0", "OpenDRT", "Linear").forEach { cs ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedColorScience = cs
                                    showColorScienceDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedColorScience == cs,
                                onClick = {
                                    selectedColorScience = cs
                                    showColorScienceDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cs)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorScienceDialog = false }) { Text(stringRes { cancel }) }
            }
        )
    }

    // GPU dialog
    if (showGpuDialog) {
        AlertDialog(
            onDismissRequest = { showGpuDialog = false },
            title = { Text(stringRes { settingsGpuBackend }) },
            text = {
                Column {
                    listOf("OpenGL ES", "Vulkan", "Auto").forEach { gpu ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGpuBackend = gpu
                                    showGpuDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedGpuBackend == gpu,
                                onClick = {
                                    selectedGpuBackend = gpu
                                    showGpuDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(gpu)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGpuDialog = false }) { Text(stringRes { cancel }) }
            }
        )
    }

    if (showClearCacheDialog) {
        ConfirmDialog(
            title = stringRes { settingsClearCacheTitle },
            message = stringRes { settingsClearCacheMessage },
            confirmText = stringRes { clear },
            onConfirm = {
                showClearCacheDialog = false
            },
            onDismiss = { showClearCacheDialog = false },
            isDestructive = true
        )
    }

    if (showClearModelsDialog) {
        ConfirmDialog(
            title = stringRes { settingsClearModelsTitle },
            message = stringRes { settingsClearModelsMessage },
            confirmText = stringRes { clear },
            onConfirm = {
                showClearModelsDialog = false
            },
            onDismiss = { showClearModelsDialog = false },
            isDestructive = true
        )
    }

    if (showDeleteDataDialog) {
        ConfirmDialog(
            title = "Delete All Data",
            message = "This will permanently delete all your data including images metadata, ratings, collections, edit history, and cached files. This action cannot be undone.",
            confirmText = "Delete Everything",
            onConfirm = {
                showDeleteDataDialog = false
                scope.launch {
                    PrivacyManager.deleteAllUserData(context)
                }
            },
            onDismiss = { showDeleteDataDialog = false },
            isDestructive = true
        )
    }

    if (showExportProgressDialog) {
        AlertDialog(
            onDismissRequest = { showExportProgressDialog = false },
            title = { Text("Export Your Data") },
            text = {
                Column {
                    Text("Your data will be exported to:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "/sdcard/AlcedoExport/alcedo_data_export/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("File paths and personal information are excluded from the export for your privacy.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportProgressDialog = false
                    scope.launch {
                        val outputDir = java.io.File("/sdcard/AlcedoExport")
                        PrivacyManager.exportUserData(context, outputDir)
                    }
                }) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportProgressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun getThemeVariantDisplayName(variant: AlcedoThemeVariant): String {
    return when (variant) {
        AlcedoThemeVariant.GOLD -> stringRes { themeGold }
        AlcedoThemeVariant.WINE -> stringRes { themeWine }
        AlcedoThemeVariant.STEEL -> stringRes { themeSteel }
        AlcedoThemeVariant.GRAPHITE -> stringRes { themeGraphite }
        AlcedoThemeVariant.MIST -> stringRes { themeMist }
        AlcedoThemeVariant.DYNAMIC -> stringRes { themeDynamic }
        AlcedoThemeVariant.HASSELBLAD -> stringRes { themeHasselblad }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
