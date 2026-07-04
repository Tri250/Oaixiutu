package com.alcedo.studio.i18n

/**
 * Interface for all localizable strings in the app.
 * Each language provides an implementation of this interface.
 */
interface StringResources {
    // ── Navigation ──────────────────────────────────────────────────────
    val navAlbum: String
    val navEditor: String
    val navAi: String
    val navSettings: String
    val navAiSearch: String

    // ── Common ──────────────────────────────────────────────────────────
    val ok: String
    val cancel: String
    val delete: String
    val save: String
    val apply: String
    val reset: String
    val loading: String
    val error: String
    val close: String
    val confirm: String
    val done: String
    val back: String
    val search: String
    val clear: String
    val refresh: String
    val download: String
    val activate: String
    val deactivate: String
    val add: String
    val edit: String
    val rename: String
    val select: String
    val selectAll: String
    val deselectAll: String
    val share: String
    val export: String
    val import: String
    val browse: String
    val retry: String
    val skip: String
    val next: String
    val previous: String
    val yes: String
    val no: String
    val enabled: String
    val disabled: String
    val active: String
    val inactive: String
    val on: String
    val off: String
    val system: String
    val light: String
    val dark: String

    // ── Album ───────────────────────────────────────────────────────────
    val albumTitle: String
    val albumSearchHint: String
    val albumNoImages: String
    val albumNoImagesDesc: String
    val albumImportPhotos: String
    val albumImportTitle: String
    val albumSelectFiles: String
    val albumSelectDirectory: String
    val albumDragDrop: String
    val albumSortDate: String
    val albumSortName: String
    val albumSortRating: String
    val albumSortType: String
    val albumImagesCount: String // "{count} images"
    val albumSelectedCount: String // "{count} selected"
    val albumFolders: String
    val albumAllImages: String
    val albumFilesCount: String // "{count} files"
    val albumFilterTitle: String
    val albumFilterReset: String
    val albumFilterApply: String
    val albumFilterCameraMake: String
    val albumFilterCameraModel: String
    val albumFilterLensModel: String
    val albumFilterLensPlaceholder: String
    val albumFilterDateRange: String
    val albumFilterStart: String
    val albumFilterEnd: String
    val albumFilterRating: String
    val albumFilterAndUp: String
    val albumFilterFileType: String
    val albumFilterAiLabels: String
    val albumFilterSavePreset: String
    val albumFilterPresetName: String
    val albumFilterSavePresetTitle: String
    val albumExportTitle: String
    val albumExportPath: String

    // ── Editor ──────────────────────────────────────────────────────────
    val editorTitle: String
    val editorPanelBasic: String
    val editorPanelCurve: String
    val editorPanelColor: String
    val editorPanelHsl: String
    val editorPanelGeometry: String
    val editorPanelEffects: String
    val editorPanelRaw: String
    val editorPanelHistory: String
    val editorUndo: String
    val editorRedo: String
    val editorCompare: String
    val editorScopeAnalyzer: String
    val editorSave: String
    val editorExport: String
    val editorBefore: String
    val editorAfter: String
    val editorNoImage: String
    val editorImagePreview: String
    val editorProcessing: String

    // Basic panel
    val editorSectionLight: String
    val editorExposure: String
    val editorContrast: String
    val editorHighlights: String
    val editorShadows: String
    val editorMidtones: String
    val editorSectionWhiteBalance: String
    val editorTemperature: String
    val editorTint: String
    val editorSectionColor: String
    val editorSaturation: String
    val editorVibrance: String
    val editorSectionSplitToning: String
    val editorHighlightHue: String
    val editorHighlightStrength: String
    val editorShadowHue: String
    val editorShadowStrength: String
    val editorBalance: String

    // Tone Curve panel
    val editorToneCurve: String
    val editorToneCurveLuminance: String
    val editorToneCurveRed: String
    val editorToneCurveGreen: String
    val editorToneCurveBlue: String

    // Color panel
    val editorColorWheels: String
    val editorLift: String
    val editorGamma: String
    val editorGain: String
    val editorReset: String // "Reset {wheel}"
    val editorHsl: String
    val editorHue: String
    val editorLuminance: String
    val editorChannelMixer: String
    val editorMonochrome: String
    val editorOutput: String

    // HSL channel names
    val editorColorRed: String
    val editorColorOrange: String
    val editorColorYellow: String
    val editorColorGreen: String
    val editorColorCyan: String
    val editorColorBlue: String
    val editorColorPurple: String
    val editorColorMagenta: String

    // Geometry panel
    val editorSectionTransform: String
    val editorRotate: String
    val editorFlipH: String
    val editorFlipV: String
    val editorAutoStraighten: String
    val editorSectionCrop: String
    val editorAspectRatio: String
    val editorCropLeft: String
    val editorCropTop: String
    val editorCropRight: String
    val editorCropBottom: String
    val editorSectionPerspective: String
    val editorHorizontal: String
    val editorVertical: String
    val editorSectionLensCorrection: String
    val editorDistortion: String
    val editorVignette: String

    // Effects panel
    val editorSectionFilmGrain: String
    val editorIntensity: String
    val editorSectionHalation: String
    val editorSpread: String
    val editorThreshold: String
    val editorRedBias: String
    val editorSectionSharpen: String
    val editorAmount: String
    val editorSectionClarity: String
    val editorRadius: String
    val editorSectionLut: String
    val editorSelectLut: String
    val editorLutBrowser: String
    val editorSearchLuts: String

    // RAW panel
    val editorDemosaicAlgorithm: String
    val editorHighlightReconstruction: String
    val editorAutoBrightness: String
    val editorUseCameraMatrix: String

    // Scope Analyzer
    val editorHistogram: String
    val editorWaveform: String
    val editorVectorscope: String
    val editorChromaticity: String
    val editorChannelRgb: String
    val editorChannelRed: String
    val editorChannelGreen: String
    val editorChannelBlue: String
    val editorChannelLuminance: String
    val editorScaleLinear: String
    val editorScaleLog: String
    val editorModeRgbParade: String
    val editorModeLuminance: String
    val editorModeOverlay: String

    // History panel
    val editorHistory: String
    val editorCreateVersion: String
    val editorDeleteVersion: String
    val editorRenameVersion: String
    val editorCloneHistory: String

    // ── Export ──────────────────────────────────────────────────────────
    val exportTitle: String
    val exportImage: String
    val exportFormat: String
    val exportQuality: String // "Quality: {n}%"
    val exportBitDepth: String
    val export8Bit: String
    val export16Bit: String
    val exportColorSpace: String
    val exportEmbedIcc: String
    val exportIncludeMetadata: String
    val exportHdrOutput: String
    val exportMaxDimension: String
    val exportMaxWidth: String
    val exportMaxHeight: String
    val exportOutputPath: String
    val exportNoLimit: String
    val exportBatchExport: String
    val exportBatchSelectDesc: String
    val exportBatchSelected: String // "{count} images selected"
    val exportAddImages: String
    val exportExporting: String
    val exportOverall: String
    val exportCurrent: String
    val exportCompleted: String
    val exportCancelled: String
    val exportError: String
    val exportSuccess: String // "Export successful: {path}"
    val exportFailed: String // "Export failed: {message}"
    val exportBatchResult: String // "Batch complete: {success} succeeded, {failed} failed"

    // ── AI ──────────────────────────────────────────────────────────────
    val aiSearchTitle: String
    val aiSearchHint: String
    val aiSearchPlaceholder: String
    val aiSemanticSearch: String
    val aiLabelFilter: String
    val aiLabelFilterTitle: String
    val aiSearching: String
    val aiResultsCount: String // "{count} results"
    val aiRelevanceSort: String
    val aiNoResults: String
    val aiTrySemantic: String
    val aiEmptyDesc: String
    val aiEmptySubDesc: String
    val aiModelLoaded: String
    val aiModelLoading: String
    val aiModelLoadFailed: String
    val aiModelNotLoaded: String
    val aiNoActiveModel: String
    val aiIndexed: String
    val aiRatingTitle: String
    val aiRatingMood: String
    val aiRatingStyle: String
    val aiStartRating: String
    val aiRatingInProgress: String
    val aiRatingResult: String
    val aiRatingHistory: String
    val aiRatingEmpty: String
    val aiRatingEmptyDesc: String
    val aiRatingDescription: String
    val aiSelectMoodTitle: String
    val aiProviderTitle: String
    val aiNoApiKey: String
    val aiAddApiKeyTitle: String
    val aiProviderLabel: String
    val aiApiKeyLabel: String
    val aiKeySecureNote: String
    val aiWrittenToExif: String
    val aiNotWrittenToExif: String
    val aiModelManager: String
    val aiAvailableModels: String
    val aiStorageSpace: String
    val aiAvailable: String
    val aiEmbeddingDim: String
    val aiMinSdk: String
    val aiModelStorageNote: String
    val aiDeleteModelTitle: String
    val aiDeleteModelMessage: String
    val aiModelNotDownloaded: String
    val aiModelDownloading: String
    val aiModelDownloaded: String
    val aiModelVerified: String
    val aiModelActivated: String
    val aiModelFailed: String
    val aiModelPaused: String

    // Query type badges
    val aiQueryExact: String
    val aiQueryExif: String
    val aiQuerySemantic: String
    val aiQueryLabel: String
    val aiResultExact: String
    val aiResultSemantic: String
    val aiResultExif: String
    val aiResultLabel: String

    // ── Settings ────────────────────────────────────────────────────────
    val settingsTitle: String
    val settingsAppearance: String
    val settingsTheme: String
    val settingsThemeSelect: String
    val settingsLanguage: String
    val settingsLanguageSelect: String
    val settingsProcessing: String
    val settingsGpuBackend: String
    val settingsColorScience: String
    val settingsStorage: String
    val settingsCacheSize: String
    val settingsClearThumbnails: String
    val settingsClearModels: String
    val settingsExportCachePath: String
    val settingsAiModels: String
    val settingsAbout: String
    val settingsAboutDesc: String
    val settingsStatistics: String
    val settingsStatisticsDesc: String
    val settingsThemeVariant: String
    val settingsThemeVariantSelect: String

    // Dialog messages
    val settingsClearCacheTitle: String
    val settingsClearCacheMessage: String
    val settingsClearModelsTitle: String
    val settingsClearModelsMessage: String

    // ── Aspect Ratios ───────────────────────────────────────────────────
    val ratioFree: String
    val ratioOriginal: String
    val ratio1_1: String
    val ratio4_3: String
    val ratio3_2: String
    val ratio16_9: String
    val ratio16_10: String
    val ratio21_9: String

    // ── Gamut Overlays ──────────────────────────────────────────────────
    val gamutSrgb: String
    val gamutP3: String
    val gamutRec2020: String
    val gamutAces: String

    // ── Theme Variants ──────────────────────────────────────────────────
    val themeGold: String
    val themeWine: String
    val themeSteel: String
    val themeGraphite: String
    val themeMist: String
    val themeDynamic: String

    // ── Language Names (native) ─────────────────────────────────────────
    val langEnglish: String
    val langChineseSimplified: String
    val langChineseTraditional: String
    val langJapanese: String
    val langKorean: String
    val langGerman: String
    val langFrench: String
    val langSpanish: String
    val langSystemDefault: String
}

/**
 * Global access point for localized strings.
 * Updated by LanguageManager when language changes.
 */
object Strings {
    var current: StringResources = EnglishStrings()
        internal set

    fun update(resources: StringResources) {
        current = resources
    }
}
