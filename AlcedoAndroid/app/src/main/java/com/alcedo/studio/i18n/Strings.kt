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
    val navCreate: String
    val navMine: String
    val navStats: String
    val navAiRating: String
    val navAiModels: String

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
    val editorPanelDisplayTransform: String
    val editorPanelLmt: String
    val editorPanelInspector: String
    val editorPanelLensCorrection: String
    val editorPanelMasks: String
    val editorUndo: String
    val editorRedo: String
    val editorCompare: String
    val editorScopeAnalyzer: String
    val editorClippingWarning: String
    val editorSave: String
    val editorExport: String
    val editorBefore: String
    val editorAfter: String
    val editorNoImage: String
    val editorImagePreview: String
    val editorProcessing: String
    val editorFocusMode: String
    val editorFocusModeOn: String
    val editorFocusModeOff: String
    val colorSpaceWorkflow: String

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
    val toneCurveReset: String
    val toneCurveHighlights: String
    val toneCurveLights: String
    val toneCurveDarks: String
    val toneCurveShadows: String
    val toneCurveSigmoidContrast: String
    val editorSigmoidShoulder: String
    val toneCurvePointMode: String
    val toneCurveParametricMode: String

    // Color panel
    val editorColorWheels: String
    val editorLift: String
    val editorGamma: String
    val editorGain: String
    val editorReset: String // "Reset {wheel}"
    val colorResetWheels: String
    val editorHsl: String
    val colorResetHsl: String
    val editorHue: String
    val editorLuminance: String
    val editorChannelMixer: String
    val colorResetMixer: String
    val editorMonochrome: String
    val editorOutput: String
    val editorOutputR: String
    val editorOutputG: String
    val editorOutputB: String

    // HSL channel names
    val editorColorRed: String
    val editorColorOrange: String
    val editorColorYellow: String
    val editorColorGreen: String
    val editorColorCyan: String
    val editorColorBlue: String
    val editorColorPurple: String
    val editorColorMagenta: String

    // HSL Profile panel
    val hlsProfileTitle: String
    val hlsHueShift: String
    val hlsLightness: String
    val hlsSaturation: String
    val hlsHueRange: String

    // Geometry panel
    val editorSectionTransform: String
    val geometryResetTransform: String
    val editorRotate: String
    val editorFlipH: String
    val editorFlipV: String
    val geometryResetRotation: String
    val editorAutoStraighten: String
    val editorSectionCrop: String
    val geometryResetCrop: String
    val editorAspectRatio: String
    val editorCropLeft: String
    val editorCropTop: String
    val editorCropRight: String
    val editorCropBottom: String
    val editorSectionPerspective: String
    val geometryResetPerspective: String
    val editorHorizontal: String
    val editorVertical: String
    val editorSectionLensCorrection: String
    val geometryResetLens: String
    val editorDistortion: String
    val geometryDistortionK1: String
    val geometryK2: String
    val editorVignette: String
    val ratio2_1: String
    val ratio3_1: String
    val ratio9_16: String

    // Composition & perspective overlays
    val cropOverlayNone: String
    val cropOverlayThirds: String
    val cropOverlayDiagonal: String
    val cropOverlayTriangle: String
    val cropOverlayGoldenSpiral: String
    val cropOverlayPhiGrid: String
    val cropOverlayArmature: String
    val cropOverlayCycle: String
    val cropCompositionGuide: String
    val cropPerspectiveTransform: String
    val cropDistortion: String
    val cropVerticalPerspective: String
    val cropHorizontalPerspective: String
    val cropRotationFine: String
    val cropAspect: String
    val cropScale: String
    val cropXOffset: String
    val cropYOffset: String
    val cropLensCorrection: String
    val cropLensAutoDetect: String
    val cropLensMaker: String
    val cropLensModel: String
    val cropCorrectDistortion: String
    val cropCorrectVignette: String
    val cropCorrectTca: String
    val cropAmount: String

    // Effects panel
    val editorSectionLuminanceDenoise: String
    val editorSectionChromaDenoise: String
    val editorStrength: String
    val editorDetailPreserve: String
    val editorColorThreshold: String
    val effectsResetLumaDenoise: String
    val effectsResetChromaDenoise: String
    val editorSectionFilmGrain: String
    val effectsResetGrain: String
    val editorIntensity: String
    val editorSectionHalation: String
    val effectsResetHalation: String
    val editorSpread: String
    val editorThreshold: String
    val editorRedBias: String
    val editorSectionSharpen: String
    val effectsResetSharpen: String
    val effectsShowSharpeningMask: String
    val editorAmount: String
    val editorSectionClarity: String
    val effectsResetClarity: String
    val editorRadius: String
    val editorSectionVignette: String
    val effectsResetVignette: String
    val effectsStrength: String
    val editorSectionLut: String
    val effectsResetLut: String
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
    val historyVersions: String
    val historyUndo: String
    val historyRedo: String
    val editorCloneHistory: String
    val historyNewVersion: String
    val historyVersionName: String
    val historyCreate: String
    val historyCancel: String
    val historyRename: String
    val historyDelete: String
    val historyRecentEdits: String
    val historySwitchToVersion: String
    val historyDeleteVersionTitle: String
    val historyDeleteVersionConfirm: String
    val editorCreateVersion: String
    val editorDeleteVersion: String
    val editorRenameVersion: String

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
    val exportHasselbladWatermark: String
    val exportWatermarkDescription: String
    val exportUseOriginalFilename: String
    val exportPathDefault: String

    // ── Share (国内社交平台) ────────────────────────────────────────────
    val shareImage: String
    val shareToWechat: String
    val shareToWeibo: String
    val shareToRednote: String
    val shareToOther: String

    // ── Accessibility ───────────────────────────────────────────────────
    val accColorWheel: String
    val accToneCurve: String
    val accCropOverlay: String
    val accCompareView: String

    // ── Additional Export / UX ──────────────────────────────────────────
    val compareBefore: String
    val compareAfter: String
    val loadingImages: String
    val noImagesFound: String
    val permissionRequired: String
    val permissionDenied: String
    val grantPermission: String
    val openSettings: String
    val lowStorageWarning: String
    val exportProgress: String
    val exportEta: String
    val cancelExport: String
    val confirmCancel: String
    val confirmDelete: String
    val confirmDeleteMessage: String
    val resetAll: String
    val resetAllConfirm: String
    val doubleTapReset: String
    val pinchToZoom: String
    val dragToCompare: String
    val editorReady: String
    val processingImage: String
    val storageFull: String
    val networkError: String

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
    val themeProDark: String
    val themeHasselblad: String
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

    // ── 设置页补充 ────────────────────────────────────────────────────
    val settingsAlbumSection: String
    val settingsEditorSection: String
    val settingsPrivacySection: String
    val settingsDefaultSort: String
    val settingsThumbnailQuality: String
    val settingsDefaultExportFormat: String
    val settingsDefaultExportQuality: String
    val settingsDefaultColorSpace: String
    val settingsLocalAi: String
    val settingsLocalAiDesc: String
    val settingsCrashReport: String
    val settingsCrashReportDesc: String
    val settingsUsageAnalytics: String
    val settingsUsageAnalyticsDesc: String
    val settingsVersionInfo: String
    val settingsDeveloper: String
    val settingsSelectExportPath: String
    val thumbnailQualityHigh: String
    val thumbnailQualityStandard: String
    val thumbnailQualityLow: String
    val exportQualityHigh: String
    val exportQualityMedium: String
    val exportQualityLow: String
    val cacheCalculating: String

    // ── AI 评分页 ──────────────────────────────────────────────────────
    val aiRatingAnalyze: String
    val aiRatingEvaluating: String
    val aiRatingScoreOverview: String
    val aiRatingAverageScore: String
    val aiRatingExcellent: String
    val aiRatingTotal: String
    val aiRatingAnalyzeDesc: String
    val aiRatingStartWithCount: String
    val aiRatingComposition: String
    val aiRatingExposure: String
    val aiRatingColorScore: String
    val aiRatingSharpness: String
    val aiRatingEditImage: String
    val aiRatingNoImages: String
    val aiRatingNoImagesDesc: String

    // ── AI 模型管理页 ──────────────────────────────────────────────────
    val aiModelPrivacy: String
    val aiModelPrivacyDesc: String
    val aiModelUsage: String
    val aiModelRequired: String
    val aiModelDeleteTitle: String
    val aiModelDeleteMessage: String
    val aiModelDownloadFailed: String

    // ── 编辑器补充 ─────────────────────────────────────────────────────
    val editorUnsavedTitle: String
    val editorUnsavedMessage: String
    val editorSaveAndExit: String
    val editorDiscardAndExit: String
    val editorExitWithoutSave: String
    val editorAutoEnhance: String
    val editorPresets: String

    // ── Preset Management (RapidRAW-inspired) ──────────────────────────
    val presetTitle: String
    val presetSearch: String
    val presetCreate: String
    val presetImport: String
    val presetExportAll: String
    val presetCategoryAll: String
    val presetCategoryBuiltin: String
    val presetCategoryCustom: String
    val presetCategoryFilm: String
    val presetCategoryPortrait: String
    val presetCategoryLandscape: String
    val presetCategoryBW: String
    val presetCategoryStreet: String
    val presetCategoryImported: String
    val presetCategoryLut: String
    val presetApply: String
    val presetEdit: String
    val presetDelete: String
    val presetExport: String
    val presetNamePrompt: String
    val presetCategoryPrompt: String
    val presetDescriptionPrompt: String
    val presetCreated: String
    val presetDeleted: String
    val presetImported: String
    val presetExported: String
    val presetAppliedToBatch: String

    // ── Stats ──────────────────────────────────────────────────────────
    val statsLibraryOverview: String
    val statsTotalImages: String
    val statsDateDistribution: String
    val statsCameraModels: String
    val statsLensDistribution: String
    val statsRatingDistribution: String
    val statsTagCloud: String
    val statsUnrated: String
    val statsNoDateData: String
    val statsNoCameraData: String
    val statsNoLensData: String
    val statsNoRatingData: String
    val statsNoTagsData: String

    // ── Additional UI strings ─────────────────────────────────────────
    val backgroundTasks: String
    val collapse: String
    val expand: String
    val completed: String
    val activeTasks: String // "{count} active tasks"
    val rate: String
    val addToCollection: String
    val copyAdjustments: String
    val pasteAdjustments: String
    val analyzeWithAi: String
    val about: String
    val addImages: String
    val folders: String
    val filter: String
    val settings: String
    val importText: String
    val aiTag: String
    val starRating: String // "{n} stars"

    // Album export dialog
    val exportImages: String
    val exportSelectImages: String // "Select Images ({selected}/{total})"
    val noImagesAvailable: String
    val exportMoreImages: String // "... and {n} more images"
    val exportPreset: String
    val exportPresetWeb: String
    val exportPresetPrint: String
    val exportPresetArchive: String
    val exportNImages: String // "Export {n} Images"

    // Basic panel additions
    val editorResetLight: String
    val editorWhites: String
    val editorBlacks: String
    val editorResetWb: String
    val editorPresence: String
    val editorResetPresence: String
    val editorResetSplitTone: String

    // Collections panel
    val collections: String
    val newCollection: String
    val noCollectionsYet: String
    val createCollection: String
    val deleteCollection: String
    val deleteCollectionMessage: String // "Are you sure you want to delete \"{name}\"? This cannot be undone."
    val noImagesInCollection: String
    val remove: String
    val name: String
    val descriptionOptional: String
    val create: String
    val renameCollection: String

    // Editor compare
    val editorCompareBefore: String
    val editorCompareAfter: String

    // Display Transform panel
    val displayTransformOdtMethod: String
    val displayTransformOutputColorSpace: String
    val displayTransformPeakLuminance: String
    val displayTransformOpenDrtLook: String
    val displayTransformOpenDrtTonescale: String
    val displayTransformCreativeWhitePoint: String

    // LMT panel
    val lmtTitle: String
    val lmtImportLut: String
    val lmtActiveLut: String
    val lmtIntensity: String
    val lmtBuiltInPresets: String

    // Image Inspector panel
    val inspectorNoImage: String
    val inspectorFileInfo: String
    val inspectorExifData: String
    val inspectorRating: String
    val inspectorTags: String
    val inspectorAiAnalysis: String
    val inspectorNoExif: String
    val inspectorClear: String
    val inspectorAddTag: String
    val inspectorAdd: String
    val inspectorQualityScore: String

    // EXIF Editor panel
    val exifEditTitle: String
    val exifAuthor: String
    val exifCopyright: String
    val exifTitle: String
    val exifComment: String
    val exifDateTime: String
    val exifDateTimeOriginal: String
    val exifMake: String
    val exifModel: String
    val exifLensModel: String
    val exifFocalLength: String
    val exifFNumber: String
    val exifIso: String
    val exifExposureTime: String
    val exifGpsLatitude: String
    val exifGpsLongitude: String
    val exifGpsClear: String
    val exifSave: String
    val exifReset: String
    val exifSaved: String
    val exifSaveFailed: String
    val exifGpsCleared: String

    // Watermark panel
    val watermarkTitle: String
    val watermarkTypeText: String
    val watermarkTypeImage: String
    val watermarkTypeTextWithLogo: String
    val watermarkText: String
    val watermarkImage: String
    val watermarkFontSize: String
    val watermarkColor: String
    val watermarkOpacity: String
    val watermarkPosition: String
    val watermarkMargin: String
    val watermarkRotation: String
    val watermarkShadow: String
    val watermarkBorder: String
    val watermarkPreview: String
    val watermarkSavePreset: String
    val watermarkPositionTopLeft: String
    val watermarkPositionTopRight: String
    val watermarkPositionBottomLeft: String
    val watermarkPositionBottomRight: String
    val watermarkPositionCenter: String
    val watermarkPositionBottomCenter: String

    // Mask panel (AI masks for local adjustments)
    val maskTitle: String
    val maskNewMask: String
    val maskSubject: String
    val maskSky: String
    val maskForeground: String
    val maskLinear: String
    val maskRadial: String
    val maskBrush: String
    val maskColorRange: String
    val maskLuminanceRange: String
    val maskWholeImage: String
    val maskAdditive: String
    val maskSubtractive: String
    val maskIntersect: String
    val maskInvert: String
    val maskOpacity: String
    val maskVisible: String
    val maskName: String
    val maskBrushSize: String
    val maskBrushHardness: String
    val maskBrushOpacity: String
    val maskBrushEdit: String
    val maskBrushDone: String
    val maskBrushDraw: String
    val maskBrushEraser: String
    val maskBrushNavigate: String
    val maskBrushUndo: String
    val maskBrushClear: String
    val maskFeather: String
    val maskAnalyzing: String
    val maskApplyTo: String
    val maskDeleteMask: String
    val maskDeleteSubMask: String

    // Common components
    val loadingDefault: String
    val loadingCancel: String
    val dialogConfirm: String
    val dialogCancel: String
    val dialogDismiss: String
    val dialogRetry: String
    val dialogOk: String
    val progressCancel: String
    val progressRemaining: String
    val etaRemaining: String
    val sectionCollapse: String
    val sectionExpand: String
    val sliderReset: String
    val compareOriginal: String
    val compareEdited: String

    // Background task bar
    val tasksCompleted: String // "{count} tasks completed"
    val switchVersionTitle: String
    val switchVersionMessage: String
    val cloneHistoryTitle: String
    val cloneHistoryMessage: String

    // Privacy
    val privacyTitle: String
    val privacyBody: String
    val privacyAnalyticsTitle: String
    val privacyAnalyticsDesc: String
    val privacyAiFeaturesTitle: String
    val privacyAiFeaturesDesc: String
    val privacyCrashReportsTitle: String
    val privacyCrashReportsDesc: String
    val privacyRequired: String
    val privacySavePreferences: String
    val privacyChangeNote: String
    val privacyDataNote: String

    // ── Batch Edit (RapidRAW-inspired copy/paste adjustments) ─────────
    val batchCopyAdjustments: String
    val batchPasteAdjustments: String
    val batchSelectivePaste: String
    val batchApplyPreset: String
    val batchResetAdjustments: String
    val batchCopySuccess: String
    val batchPasteSuccess: String
    val batchPastePartialSuccess: String
    val batchResetSuccess: String
    val batchApplyPresetSuccess: String
    val batchSelectSource: String
    val batchNoSource: String
    val batchFilterBasic: String
    val batchFilterWhiteBalance: String
    val batchFilterColor: String
    val batchFilterToneCurve: String
    val batchFilterEffects: String
    val batchFilterGeometry: String
    val batchFilterLut: String
    val batchProcessing: String
    val batchProcessed: String
    val batchFailed: String
    val batchSelectImagesFirst: String
    val batchPanelTitle: String
    val batchConfirmResetTitle: String
    val batchConfirmResetMessage: String
    val batchPickPreset: String
    val batchClipboardFrom: String
    val batchClipboardEmpty: String
    val batchSelectedCount: String // "{count} images selected"
    val batchSyncParams: String
    val batchIndividualAdjust: String

    // ── Privacy Policy & User Agreement screens ─────────────────────────
    val settingsPrivacyPolicy: String
    val settingsUserAgreement: String
    val settingsCrashReporting: String
    val settingsCrashReportingDesc: String
    val privacyPolicyTitle: String
    val userAgreementTitle: String
    val privacyAgree: String
    val privacyDisagree: String
    val privacyContent: String
    val userAgreementContent: String
    val privacyEffectiveDate: String
    val userAgreementEffectiveDate: String

    // ── About Page ──────────────────────────────────────────────────────
    val aboutAppTagline: String
    val aboutVersionInfo: String
    val aboutAppName: String
    val aboutVersion: String
    val aboutBuildNumber: String
    val aboutPlatform: String
    val aboutMinSdk: String
    val aboutArchitecture: String
    val aboutLicense: String
}

/**
 * Global access point for localized strings.
 * Updated by LanguageManager when language changes.
 */
object Strings {
    // 默认使用简体中文，适合国内用户
    var current: StringResources = ChineseSimplifiedStrings()
        internal set

    fun update(resources: StringResources) {
        current = resources
    }
}
