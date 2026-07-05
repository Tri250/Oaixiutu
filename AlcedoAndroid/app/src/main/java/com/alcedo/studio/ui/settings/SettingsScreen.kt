package com.alcedo.studio.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import com.alcedo.studio.BuildConfig
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alcedo.studio.i18n.Language
import com.alcedo.studio.i18n.LanguageManager
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.privacy.PrivacyManager
import com.alcedo.studio.ui.common.ConfirmDialog
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoThemeVariant
import com.alcedo.studio.ui.theme.ThemeManager

private val APP_VERSION = BuildConfig.VERSION_NAME.ifBlank { "1.1.5" }
private const val DEVELOPER_NAME = "带娃的小陈工"
private val DEFAULT_EXPORT_PATH = ""  // 使用系统默认存储路径

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val s = Strings.current
    val context = LocalContext.current

    // Theme mode (light/dark/system)
    val darkMode by ThemeManager.darkMode.collectAsStateWithLifecycle()
    val selectedTheme = when (darkMode) {
        "light" -> s.light
        "dark" -> s.dark
        else -> s.system
    }

    // Theme variant
    val currentVariant by ThemeManager.themeVariant.collectAsStateWithLifecycle()

    // Language
    val currentLanguage by LanguageManager.currentLanguage.collectAsStateWithLifecycle()
    val followSystem by LanguageManager.followSystem.collectAsStateWithLifecycle()
    val selectedLanguage = if (followSystem) s.langSystemDefault else currentLanguage.nativeName

    // Album / Editor settings – persisted via SharedPreferences
    val prefs = remember { context.getSharedPreferences("alcedo_settings", 0) }
    var selectedSort by remember { mutableStateOf(prefs.getString("album_sort", "date") ?: "date") }
    var selectedThumbQuality by remember { mutableStateOf(prefs.getString("thumb_quality", "high") ?: "high") }
    var selectedExportFormat by remember { mutableStateOf(prefs.getString("export_format", "jpeg") ?: "jpeg") }
    var selectedExportQuality by remember { mutableStateOf(prefs.getString("export_quality", "high") ?: "high") }
    var selectedColorSpace by remember { mutableStateOf(prefs.getString("color_space", "srgb") ?: "srgb") }

    // Helper to persist a setting
    fun persistSetting(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    // Dialog visibility states
    var showThemeDialog by remember { mutableStateOf(false) }
    var showThemeVariantDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showThumbQualityDialog by remember { mutableStateOf(false) }
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var showExportQualityDialog by remember { mutableStateOf(false) }
    var showColorSpaceDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearModelsDialog by remember { mutableStateOf(false) }

    // Privacy consent
    val consentStatus by remember { mutableStateOf(PrivacyManager.getConsentStatus()) }
    var analyticsConsent by remember { mutableStateOf(consentStatus.analytics) }
    var crashReportsConsent by remember { mutableStateOf(consentStatus.crashReports) }
    var aiProcessingConsent by remember { mutableStateOf(consentStatus.aiProcessing) }

    var exportCachePath by remember { mutableStateOf(prefs.getString("export_cache_path", DEFAULT_EXPORT_PATH) ?: DEFAULT_EXPORT_PATH) }

    // Calculate real cache size
    val cacheCalculatingText = stringRes { cacheCalculating }
    var cacheSize by remember { mutableStateOf(cacheCalculatingText) }
    LaunchedEffect(Unit) {
        cacheSize = calculateCacheSize(context)
    }

    // Resolved display values
    val sortLabel = when (selectedSort) {
        "name" -> s.albumSortName
        "rating" -> s.albumSortRating
        "type" -> s.albumSortType
        else -> s.albumSortDate
    }
    val thumbQualityLabel = when (selectedThumbQuality) {
        "standard" -> stringRes { thumbnailQualityStandard }
        "low" -> stringRes { thumbnailQualityLow }
        else -> stringRes { thumbnailQualityHigh }
    }
    val exportFormatLabel = when (selectedExportFormat) {
        "png" -> "PNG"
        "heif" -> "HEIF"
        else -> "JPEG"
    }
    val exportQualityLabel = when (selectedExportQuality) {
        "medium" -> stringRes { exportQualityMedium }
        "low" -> stringRes { exportQualityLow }
        else -> stringRes { exportQualityHigh }
    }
    val colorSpaceLabel = when (selectedColorSpace) {
        "p3" -> s.gamutP3
        "rec2020" -> s.gamutRec2020
        else -> s.gamutSrgb
    }

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
                .padding(bottom = 24.dp)
        ) {
            // ── 顶部用户信息卡 ─────────────────────────────────────────────
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(16.dp)
                                .size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Alcedo Studio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v$APP_VERSION",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── 通用设置 ─────────────────────────────────────────────────
            SettingsSectionHeader(stringRes { settingsAppearance })
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SettingsRow(
                    icon = if (darkMode == "dark") Icons.Default.DarkMode
                    else if (darkMode == "light") Icons.Default.LightMode
                    else Icons.Default.BrightnessAuto,
                    title = stringRes { settingsTheme },
                    value = selectedTheme,
                    onClick = { showThemeDialog = true }
                )
                SectionDivider()
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = stringRes { settingsThemeVariant },
                    value = getThemeVariantDisplayName(currentVariant),
                    onClick = { showThemeVariantDialog = true }
                )
                SectionDivider()
                SettingsRow(
                    icon = Icons.Default.Language,
                    title = stringRes { settingsLanguage },
                    value = selectedLanguage,
                    onClick = { showLanguageDialog = true }
                )
            }

            // ── 相册设置 ─────────────────────────────────────────────────
            SettingsSectionHeader(stringRes { settingsAlbumSection })
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SettingsRow(
                    icon = Icons.Default.Sort,
                    title = stringRes { settingsDefaultSort },
                    value = sortLabel,
                    onClick = { showSortDialog = true }
                )
                SectionDivider()
                SettingsRow(
                    icon = Icons.Default.Image,
                    title = stringRes { settingsThumbnailQuality },
                    value = thumbQualityLabel,
                    onClick = { showThumbQualityDialog = true }
                )
            }

            // ── 编辑设置 ─────────────────────────────────────────────────
            SettingsSectionHeader(stringRes { settingsEditorSection })
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SettingsRow(
                    icon = Icons.Default.FilePresent,
                    title = stringRes { settingsDefaultExportFormat },
                    value = exportFormatLabel,
                    onClick = { showExportFormatDialog = true }
                )
                SectionDivider()
                SettingsRow(
                    icon = Icons.Default.HighQuality,
                    title = stringRes { settingsDefaultExportQuality },
                    value = exportQualityLabel,
                    onClick = { showExportQualityDialog = true }
                )
                SectionDivider()
                SettingsRow(
                    icon = Icons.Default.ColorLens,
                    title = stringRes { settingsDefaultColorSpace },
                    value = colorSpaceLabel,
                    onClick = { showColorSpaceDialog = true }
                )
            }

            // ── 存储管理 ─────────────────────────────────────────────────
            SettingsSectionHeader(stringRes { settingsStorage })
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringRes { settingsCacheSize },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = cacheSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showClearCacheDialog = true }) {
                        Text(stringRes { settingsClearThumbnails })
                    }
                    TextButton(onClick = { showClearModelsDialog = true }) {
                        Text(stringRes { settingsClearModels })
                    }
                }
                SectionDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    OutlinedTextField(
                        value = exportCachePath,
                        onValueChange = {
                            exportCachePath = it
                            persistSetting("export_cache_path", it)
                        },
                        label = { Text(stringRes { settingsExportCachePath }) },
                        placeholder = { Text(stringRes { settingsSelectExportPath }) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 隐私设置 ─────────────────────────────────────────────────
            SettingsSectionHeader(stringRes { settingsPrivacySection })
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SwitchRow(
                    icon = Icons.Default.Psychology,
                    title = stringRes { settingsLocalAi },
                    subtitle = stringRes { settingsLocalAiDesc },
                    checked = aiProcessingConsent,
                    onCheckedChange = {
                        aiProcessingConsent = it
                        if (it) PrivacyManager.grantConsent(PrivacyManager.ConsentType.AI_PROCESSING)
                        else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.AI_PROCESSING)
                    }
                )
                SectionDivider()
                SwitchRow(
                    icon = Icons.Default.BugReport,
                    title = stringRes { settingsCrashReport },
                    subtitle = stringRes { settingsCrashReportDesc },
                    checked = crashReportsConsent,
                    onCheckedChange = {
                        crashReportsConsent = it
                        if (it) PrivacyManager.grantConsent(PrivacyManager.ConsentType.CRASH_REPORTS)
                        else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.CRASH_REPORTS)
                    }
                )
                SectionDivider()
                SwitchRow(
                    icon = Icons.Default.Analytics,
                    title = stringRes { settingsUsageAnalytics },
                    subtitle = stringRes { settingsUsageAnalyticsDesc },
                    checked = analyticsConsent,
                    onCheckedChange = {
                        analyticsConsent = it
                        if (it) PrivacyManager.grantConsent(PrivacyManager.ConsentType.ANALYTICS)
                        else PrivacyManager.revokeConsent(PrivacyManager.ConsentType.ANALYTICS)
                    }
                )
            }

            // 统计分析
            SettingsSectionHeader(stringRes { settingsStatistics })
            SectionDivider()
            SettingsRow(
                icon = Icons.Default.BarChart,
                title = stringRes { navStats },
                value = stringRes { settingsStatisticsDesc },
                onClick = { navController.navigate("stats") }
            )
            SectionDivider()

            // ── 关于 ─────────────────────────────────────────────────────
            SettingsSectionHeader(stringRes { settingsAbout })
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = stringRes { settingsVersionInfo },
                    value = "v$APP_VERSION"
                )
                SectionDivider()
                SettingsRow(
                    icon = Icons.Default.Description,
                    title = stringRes { settingsAbout },
                    value = stringRes { settingsAboutDesc },
                    onClick = { navController.navigate("about") }
                )
            }

            // 开发者卡片
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringRes { settingsDeveloper },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DEVELOPER_NAME,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── 底部署名 ─────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Made with ❤ by $DEVELOPER_NAME",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }

    // ── 对话框 ─────────────────────────────────────────────────────────

    // Theme mode dialog (Light/Dark/System)
    if (showThemeDialog) {
        OptionListDialog(
            title = stringRes { settingsThemeSelect },
            options = listOf(
                "system" to stringRes { system },
                "light" to stringRes { light },
                "dark" to stringRes { dark }
            ),
            selectedId = darkMode,
            onOptionSelected = {
                ThemeManager.setDarkMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    // Theme variant dialog (DEEP_SPACE first)
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
                                    AlcedoThemeVariant.DEEP_SPACE -> Icons.Default.NightsStay
                                    AlcedoThemeVariant.HASSELBLAD -> Icons.Default.PhotoCamera
                                    AlcedoThemeVariant.GOLD -> Icons.Default.Star
                                    AlcedoThemeVariant.WINE -> Icons.Default.LocalBar
                                    AlcedoThemeVariant.STEEL -> Icons.Default.Construction
                                    AlcedoThemeVariant.GRAPHITE -> Icons.Default.Brush
                                    AlcedoThemeVariant.MIST -> Icons.Default.Cloud
                                    AlcedoThemeVariant.DYNAMIC -> Icons.Default.AutoAwesome
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

    // Album sort dialog
    if (showSortDialog) {
        OptionListDialog(
            title = stringRes { settingsDefaultSort },
            options = listOf(
                "date" to s.albumSortDate,
                "name" to s.albumSortName,
                "rating" to s.albumSortRating,
                "type" to s.albumSortType
            ),
            selectedId = selectedSort,
            onOptionSelected = {
                selectedSort = it
                persistSetting("album_sort", it)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    // Thumbnail quality dialog
    if (showThumbQualityDialog) {
        OptionListDialog(
            title = stringRes { settingsThumbnailQuality },
            options = listOf(
                "high" to stringRes { thumbnailQualityHigh },
                "standard" to stringRes { thumbnailQualityStandard },
                "low" to stringRes { thumbnailQualityLow }
            ),
            selectedId = selectedThumbQuality,
            onOptionSelected = {
                selectedThumbQuality = it
                persistSetting("thumb_quality", it)
                showThumbQualityDialog = false
            },
            onDismiss = { showThumbQualityDialog = false }
        )
    }

    // Export format dialog
    if (showExportFormatDialog) {
        OptionListDialog(
            title = stringRes { settingsDefaultExportFormat },
            options = listOf(
                "jpeg" to "JPEG",
                "png" to "PNG",
                "heif" to "HEIF"
            ),
            selectedId = selectedExportFormat,
            onOptionSelected = {
                selectedExportFormat = it
                persistSetting("export_format", it)
                showExportFormatDialog = false
            },
            onDismiss = { showExportFormatDialog = false }
        )
    }

    // Export quality dialog
    if (showExportQualityDialog) {
        OptionListDialog(
            title = stringRes { settingsDefaultExportQuality },
            options = listOf(
                "high" to stringRes { exportQualityHigh },
                "medium" to stringRes { exportQualityMedium },
                "low" to stringRes { exportQualityLow }
            ),
            selectedId = selectedExportQuality,
            onOptionSelected = {
                selectedExportQuality = it
                persistSetting("export_quality", it)
                showExportQualityDialog = false
            },
            onDismiss = { showExportQualityDialog = false }
        )
    }

    // Color space dialog
    if (showColorSpaceDialog) {
        OptionListDialog(
            title = stringRes { settingsDefaultColorSpace },
            options = listOf(
                "srgb" to s.gamutSrgb,
                "p3" to s.gamutP3,
                "rec2020" to s.gamutRec2020
            ),
            selectedId = selectedColorSpace,
            onOptionSelected = {
                selectedColorSpace = it
                persistSetting("color_space", it)
                showColorSpaceDialog = false
            },
            onDismiss = { showColorSpaceDialog = false }
        )
    }

    if (showClearCacheDialog) {
        ConfirmDialog(
            title = stringRes { settingsClearCacheTitle },
            message = stringRes { settingsClearCacheMessage },
            confirmText = stringRes { clear },
            onConfirm = {
                // Actually clear thumbnail and temp caches
                clearAppCache(context)
                cacheSize = calculateCacheSize(context)
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
                // Actually delete AI model files
                clearAiModels(context)
                cacheSize = calculateCacheSize(context)
                showClearModelsDialog = false
            },
            onDismiss = { showClearModelsDialog = false },
            isDestructive = true
        )
    }
}

@Composable
private fun getThemeVariantDisplayName(variant: AlcedoThemeVariant): String {
    return when (variant) {
        AlcedoThemeVariant.DEEP_SPACE -> variant.displayName
        AlcedoThemeVariant.HASSELBLAD -> stringRes { themeHasselblad }
        AlcedoThemeVariant.GOLD -> stringRes { themeGold }
        AlcedoThemeVariant.WINE -> stringRes { themeWine }
        AlcedoThemeVariant.STEEL -> stringRes { themeSteel }
        AlcedoThemeVariant.GRAPHITE -> stringRes { themeGraphite }
        AlcedoThemeVariant.MIST -> stringRes { themeMist }
        AlcedoThemeVariant.DYNAMIC -> stringRes { themeDynamic }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        thickness = 0.5.dp
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionListDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == id,
                            onClick = { onOptionSelected(id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringRes { cancel }) }
        }
    )
}

// ── Real cache management ──────────────────────────────────
private fun calculateCacheSize(context: android.content.Context): String {
    var totalBytes = 0L
    try {
        // Thumbnail cache
        val thumbDir = java.io.File(context.cacheDir, "thumbnails")
        if (thumbDir.exists()) totalBytes += thumbDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        // Temp files
        val tempDir = java.io.File(context.cacheDir, "temp")
        if (tempDir.exists()) totalBytes += tempDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        // AI model files
        val modelsDir = java.io.File(context.filesDir, "ai_models")
        if (modelsDir.exists()) totalBytes += modelsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        // General cache
        totalBytes += context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {}
    return formatFileSize(totalBytes)
}

private fun clearAppCache(context: android.content.Context) {
    try {
        val thumbDir = java.io.File(context.cacheDir, "thumbnails")
        thumbDir.deleteRecursively()
        val tempDir = java.io.File(context.cacheDir, "temp")
        tempDir.deleteRecursively()
        context.cacheDir.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.forEach { it.delete() }
    } catch (_: Exception) {}
}

private fun clearAiModels(context: android.content.Context) {
    try {
        val modelsDir = java.io.File(context.filesDir, "ai_models")
        modelsDir.deleteRecursively()
    } catch (_: Exception) {}
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
