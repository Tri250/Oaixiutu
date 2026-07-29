package com.alcedo.studio.i18n

/**
 * String table interface. Every UI string lives here so screens stay free of
 * hard-coded text and the language can be swapped at runtime by [Strings].
 *
 * Implementations: [StringResourcesEn], [StringResourcesZhCn].
 */
interface StringRes {

    // ---- App / navigation ----
    val appName: String
    val tabAlbum: String
    val tabEditor: String
    val tabAi: String
    val tabSettings: String

    // ---- Album ----
    val albumTitle: String
    val search: String
    val searchHint: String
    val searchSemantic: String
    val import: String
    val createFolder: String
    val selectAll: String
    val clearSelection: String
    val emptyAlbumTitle: String
    val emptyAlbumSubtitle: String
    val noSearchResults: String
    val filter: String
    val sort: String
    val sortByDateCaptured: String
    val sortByDateAdded: String
    val sortByName: String
    val sortByRating: String
    val sortByFileSize: String
    val sortByAiScore: String
    val ascending: String
    val descending: String
    val gridView: String
    val listView: String
    val thumbnailSize: String
    val collections: String
    val allPhotos: String
    val imports: String
    val virtualCopies: String
    val trash: String
    val inspector: String
    val stats: String
    val statTotalImages: String
    val statRawImages: String
    val statPicks: String
    val statRejects: String
    val statFolders: String
    val statRated: String
    val statTagged: String
    val byCamera: String
    val byLens: String

    // ---- Context menu / actions ----
    val rate: String
    val flag: String
    val flagPick: String
    val flagReject: String
    val flagClear: String
    val colorLabel: String
    val delete: String
    val addToCollection: String
    val copyAdjustments: String
    val pasteAdjustments: String
    val batchEdit: String
    val cullWithAi: String
    val edit: String
    val export: String
    val exportSelected: String
    val openInEditor: String
    val hide: String
    val unhide: String

    // ---- Batch edit ----
    val batchEditTitle: String
    val batchApplyPreset: String
    val batchSyncSettings: String
    val batchClearAdjustments: String
    val batchApply: String

    // ---- Export dialog ----
    val exportTitle: String
    val format: String
    val quality: String
    val resolution: String
    val maxDimension: String
    val colorSpace: String
    val includeMetadata: String
    val ultraHdr: String
    val watermark: String
    val outputDirectory: String
    val namingPattern: String
    val exportButton: String
    val exportCancel: String
    val exporting: String
    val exportComplete: String
    val exportFailed: String

    // ---- Editor ----
    val editorTitle: String
    val panelTone: String
    val panelLook: String
    val panelDisplay: String
    val panelGeometry: String
    val panelRaw: String
    val panelColor: String
    val panelToneCurve: String
    val panelColorWheels: String
    val panelHsl: String
    val panelEffects: String
    val panelMasks: String
    val panelPresets: String
    val panelHistory: String
    val panelExif: String
    val panelLensCorrection: String
    val panelLmt: String
    val panelWatermark: String
    val undo: String
    val redo: String
    val reset: String
    val resetAll: String
    val beforeAfter: String
    val fit: String
    val zoomIn: String
    val zoomOut: String
    val compare: String
    val createVersion: String
    val createVirtualCopy: String
    val cloneVersion: String
    val deleteVersion: String
    val savePreset: String
    val applyPreset: String

    // ---- Tone panel ----
    val exposure: String
    val contrast: String
    val highlights: String
    val shadows: String
    val whites: String
    val blacks: String
    val temperature: String
    val tint: String
    val saturation: String
    val vibrance: String
    val clarity: String
    val sharpen: String

    // ---- Color / HSL ----
    val hsl: String
    val hue: String
    val saturationBand: String
    val luminance: String
    val bandRed: String
    val bandOrange: String
    val bandYellow: String
    val bandGreen: String
    val bandAqua: String
    val bandBlue: String
    val bandPurple: String
    val bandMagenta: String

    // ---- Color wheels ----
    val lift: String
    val gamma: String
    val gain: String
    val colorWheels: String
    val hueAngle: String
    val saturationAmount: String
    val luminanceAmount: String

    // ---- Tone curve ----
    val curveMaster: String
    val curveRed: String
    val curveGreen: String
    val curveBlue: String
    val curveLuma: String
    val addPoint: String
    val removePoint: String

    // ---- Effects ----
    val filmGrain: String
    val grainAmount: String
    val grainSize: String
    val grainRoughness: String
    val halation: String
    val halationSpread: String
    val halationIntensity: String
    val halationColor: String
    val luts: String
    val lutIntensity: String
    val loadLut: String

    // ---- Geometry ----
    val crop: String
    val rotate: String
    val flipHorizontal: String
    val flipVertical: String
    val aspectRatio: String
    val aspectFree: String
    val aspectOriginal: String
    val aspect1x1: String
    val aspect4x3: String
    val aspect3x2: String
    val aspect16x9: String
    val aspect2x1: String
    val aspect3x1: String
    val perspective: String
    val perspectiveHorizontal: String
    val perspectiveVertical: String
    val perspectiveAuto: String
    val ruleOfThirds: String
    val goldenRatio: String
    val diagonal: String
    val centerCross: String
    val compositionGuide: String
    val apply: String
    val applyCrop: String

    // ---- Display transform ----
    val displayTransform: String
    val outputColorSpace: String
    val eotf: String
    val peakLuminance: String
    val acesOpenDrt: String
    val openDrt: String
    val aces2: String
    val presets: String
    val surround: String
    val surroundDim: String
    val surroundAverage: String

    // ---- RAW ----
    val rawDecode: String
    val highlightReconstruction: String
    val highlightMethod: String
    val highlightClip: String
    val highlightReconstruct: String
    val highlightBlend: String
    val highlightColor: String
    val demosaic: String
    val blackLevel: String
    val whitePoint: String
    val noiseReduction: String
    val chromaAberration: String
    val lensProfile: String
    val lensCorrection: String
    val distortion: String
    val vignette: String
    val vignetteAmount: String
    val vignetteMidpoint: String
    val chromaAberrationR: String
    val chromaAberrationB: String

    // ---- Scopes ----
    val scopes: String
    val histogram: String
    val waveform: String
    val vectorscope: String
    val chromaticity: String

    // ---- Masks ----
    val masks: String
    val maskBrush: String
    val maskRadial: String
    val maskLinear: String
    val maskLuminance: String
    val maskSubject: String
    val maskSky: String
    val opacity: String
    val feather: String
    val invert: String
    val addMask: String

    // ---- EXIF ----
    val exif: String
    val camera: String
    val lens: String
    val focalLength: String
    val aperture: String
    val shutterSpeed: String
    val iso: String
    val exposureBias: String
    val whiteBalance: String
    val flash: String
    val dimensions: String
    val description: String
    val copyright: String

    // ---- History ----
    val history: String
    val versions: String
    val transactions: String
    val noVersions: String

    // ---- AI ----
    val aiTitle: String
    val aiSearch: String
    val aiSearchHint: String
    val aiRating: String
    val aiCulling: String
    val aiModels: String
    val generateTags: String
    val generatingTags: String
    val overallScore: String
    val technicalScore: String
    val aestheticScore: String
    val sharpnessScore: String
    val exposureScore: String
    val compositionScore: String
    val suggestedRating: String
    val issues: String
    val noIssues: String
    val modelDownload: String
    val modelDownloaded: String
    val modelNotDownloaded: String
    val downloadModel: String
    val deleteModel: String
    val clip: String
    val siglip: String
    val maskSegment: String
    val setAsDefault: String

    // ---- Onboarding ----
    val welcome: String
    val welcomeSubtitle: String
    val newProject: String
    val openProject: String
    val recentProjects: String
    val noProjects: String
    val projectName: String
    val create: String
    val open: String

    // ---- Settings ----
    val settings: String
    val settingsGeneral: String
    val settingsAppearance: String
    val settingsAi: String
    val settingsStorage: String
    val settingsAbout: String
    val language: String
    val theme: String
    val themeDark: String
    val cloudLlm: String
    val cloudLlmDesc: String
    val onDeviceAi: String
    val onDeviceAiDesc: String
    val telemetry: String
    val telemetryDesc: String
    val cacheSize: String
    val clearCache: String
    val clearCacheConfirm: String
    val sweepOrphans: String
    val restorePresets: String
    val restorePresetsConfirm: String
    val nativeVersion: String
    val gpuAvailable: String
    val manageSpace: String
    val about: String
    val version: String
    val license: String
    val credits: String
    val privacyPolicy: String
    val userAgreement: String
    val yes: String
    val no: String
    val enabled: String
    val disabled: String
    val available: String
    val unavailable: String

    // ---- Common ----
    val ok: String
    val cancel: String
    val confirm: String
    val close: String
    val save: String
    val done: String
    val loading: String
    val error: String
    val retry: String
    val dismiss: String
    val more: String
    val back: String

    // ---- Compare modes ----
    val compareSplit: String
    val compareSideBySide: String
    val compareOverlay: String
    val before: String
    val after: String

    // ---- Export (extensions) ----
    val bitDepth: String
    val resize: String
    val maintainAspectRatio: String
    val width: String
    val height: String
    val iccProfile: String
    val metadataHandling: String
    val defaultOutputDir: String

    // ---- AI Rating (extensions) ----
    val llmProvider: String
    val strictness: String
    val strictnessGenerous: String
    val strictnessCritical: String
    val analyzeImages: String
    val analyzingImages: String
    val analyzeHint: String
    val applyRatingsToExif: String
    val applyRatingsConfirm: String
    val applying: String
    val focus: String
    val motion: String
    val compShort: String
    val motionBlur: String
    val detail: String
    val ratingDetails: String

    // ---- AI Model Manager (extensions) ----
    val aiModelStorage: String
    val totalLabel: String
    val ofXGbAvailable: String
    val sizeLabel: String
    val dimsLabel: String
    val active: String
    val inactive: String

    // ---- Semantic generation (extensions) ----
    val folderToScan: String
    val defaultScanFolder: String
    val model: String
    val result: String
    val stop: String
    val startScan: String

    // ---- Settings (extensions) ----
    val defaultView: String
    val showHistogram: String
    val autoSaveInterval: String
    val autoSaveIntervalValue: String
    val gpuBackend: String
    val vulkan: String
    val cpu: String
    val apiKey: String
    val endpoint: String
    val defaultStrictness: String
    val privacy: String
    val analytics: String
    val analyticsDesc: String
    val crashReports: String
    val crashReportsDesc: String
    val diagnostics: String
    val themeLight: String
    val themeSystem: String

    // ---- About (extensions) ----
    val appTagline: String
    val versionCode: String
    val buildInfo: String
    val licenseGpl: String
    val links: String
    val github: String
    val website: String
    val thirdPartyLicenses: String
    val aboutCreditsText: String

    // ---- Privacy Policy ----
    val privacyDataCollectionTitle: String
    val privacyDataCollectionBody: String
    val privacyOnDeviceAiTitle: String
    val privacyOnDeviceAiBody: String
    val privacyTelemetryTitle: String
    val privacyTelemetryBody: String
    val privacyStorageTitle: String
    val privacyStorageBody: String
    val privacyRightsTitle: String
    val privacyRightsBody: String

    // ---- User Agreement ----
    val agreementLicenseTitle: String
    val agreementLicenseBody: String
    val agreementAcceptableUseTitle: String
    val agreementAcceptableUseBody: String
    val agreementAiFeaturesTitle: String
    val agreementAiFeaturesBody: String
    val agreementDisclaimerTitle: String
    val agreementDisclaimerBody: String

    // ---- Share panel ----
    val copyPath: String
    val share: String
    val couldNotCopyPath: String
    val noAppToOpenFile: String
    val couldNotShareFile: String
    val fileNoLongerExists: String

    // ---- Manage space ----
    val clearCacheDesc: String

    // ---- Effects (extensions) ----
    val threshold: String
    val filmSimulations: String

    // ---- Permissions ----
    val permissionRationale: String
    val grantAccess: String
}
