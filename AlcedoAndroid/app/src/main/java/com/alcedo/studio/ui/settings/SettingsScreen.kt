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
import com.alcedo.studio.data.model.AiModelProfile
import com.alcedo.studio.data.model.ColorScience
import com.alcedo.studio.data.model.GpuBackendKind
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.ui.common.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var selectedTheme by remember { mutableStateOf("System") }
    var selectedColorScience by remember { mutableStateOf("ACES 2.0") }
    var selectedGpuBackend by remember { mutableStateOf("OpenGL ES") }
    var selectedLanguage by remember { mutableStateOf("System") }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearModelsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showColorScienceDialog by remember { mutableStateOf(false) }
    var showGpuDialog by remember { mutableStateOf(false) }
    var exportCachePath by remember { mutableStateOf("/sdcard/Pictures/Alcedo") }

    val context = LocalContext.current
    val cacheSize = remember { "1.2 GB" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("ai_models") }) {
                        Icon(Icons.Default.ModelTraining, contentDescription = "AI Models")
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
            SettingsSectionHeader("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(selectedTheme) },
                leadingContent = {
                    Icon(
                        if (selectedTheme == "Dark") Icons.Default.DarkMode
                        else if (selectedTheme == "Light") Icons.Default.LightMode
                        else Icons.Default.BrightnessAuto,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            HorizontalDivider()

            // Processing
            SettingsSectionHeader("Processing")
            ListItem(
                headlineContent = { Text("GPU Backend") },
                supportingContent = { Text(selectedGpuBackend) },
                leadingContent = { Icon(Icons.Default.Memory, contentDescription = null) },
                modifier = Modifier.clickable { showGpuDialog = true }
            )
            ListItem(
                headlineContent = { Text("Color Science") },
                supportingContent = { Text(selectedColorScience) },
                leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                modifier = Modifier.clickable { showColorScienceDialog = true }
            )

            HorizontalDivider()

            // Language
            SettingsSectionHeader("Language")
            ListItem(
                headlineContent = { Text("App Language") },
                supportingContent = { Text(selectedLanguage) },
                leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )

            HorizontalDivider()

            // Storage
            SettingsSectionHeader("Storage")
            ListItem(
                headlineContent = { Text("Cache Size") },
                supportingContent = { Text(cacheSize) },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                trailingContent = {
                    Row {
                        TextButton(onClick = { showClearCacheDialog = true }) {
                            Text("Clear Thumbnails")
                        }
                        TextButton(onClick = { showClearModelsDialog = true }) {
                            Text("Clear Models")
                        }
                    }
                }
            )
            OutlinedTextField(
                value = exportCachePath,
                onValueChange = { exportCachePath = it },
                label = { Text("Export Cache Path") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
            )

            HorizontalDivider()

            // AI Models
            SettingsSectionHeader("AI Models")
            val aiService = remember { AppModule.aiService }
            val models = remember { aiService.getModelCatalog() }
            models.forEach { model ->
                AiModelItem(model = model)
            }

            HorizontalDivider()

            // About
            SettingsSectionHeader("About")
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("0.2.6-android") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text("Build") },
                supportingContent = { Text("Android 9+ (API 28)") },
                leadingContent = { Icon(Icons.Default.Android, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text("Credits") },
                supportingContent = { Text("Alcedo Studio Team") },
                leadingContent = { Icon(Icons.Default.Groups, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text("License") },
                supportingContent = { Text("Apache 2.0") },
                leadingContent = { Icon(Icons.Default.Description, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Theme dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    listOf("System", "Light", "Dark").forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTheme = theme
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTheme == theme,
                                onClick = {
                                    selectedTheme = theme
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(theme)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Language dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language") },
            text = {
                Column {
                    listOf("System", "English", "中文", "日本語", "한국어").forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguage = lang
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == lang,
                                onClick = {
                                    selectedLanguage = lang
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Color science dialog
    if (showColorScienceDialog) {
        AlertDialog(
            onDismissRequest = { showColorScienceDialog = false },
            title = { Text("Color Science") },
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
                TextButton(onClick = { showColorScienceDialog = false }) { Text("Cancel") }
            }
        )
    }

    // GPU dialog
    if (showGpuDialog) {
        AlertDialog(
            onDismissRequest = { showGpuDialog = false },
            title = { Text("GPU Backend") },
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
                TextButton(onClick = { showGpuDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearCacheDialog) {
        ConfirmDialog(
            title = "Clear Thumbnail Cache",
            message = "This will delete all cached thumbnails. Thumbnails will be regenerated when needed.",
            confirmText = "Clear",
            onConfirm = {
                showClearCacheDialog = false
                // Clear cache
            },
            onDismiss = { showClearCacheDialog = false },
            isDestructive = true
        )
    }

    if (showClearModelsDialog) {
        ConfirmDialog(
            title = "Clear AI Models",
            message = "This will delete all downloaded AI models. You will need to re-download them.",
            confirmText = "Clear",
            onConfirm = {
                showClearModelsDialog = false
                // Clear models
            },
            onDismiss = { showClearModelsDialog = false },
            isDestructive = true
        )
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

@Composable
private fun AiModelItem(model: AiModelProfile) {
    ListItem(
        headlineContent = { Text(model.modelName) },
        supportingContent = { Text(model.description) },
        leadingContent = {
            Icon(
                Icons.Default.ModelTraining,
                contentDescription = null,
                tint = if (model.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            when {
                !model.isDownloaded -> {
                    TextButton(onClick = { /* Download */ }) {
                        Text("Download")
                    }
                }
                model.isActive -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Active",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                else -> {
                    TextButton(onClick = { /* Activate */ }) {
                        Text("Activate")
                    }
                }
            }
        }
    )
}