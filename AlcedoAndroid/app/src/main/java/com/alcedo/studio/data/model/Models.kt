package com.alcedo.studio.data.model

import kotlinx.serialization.Serializable

/**
 * Core data models for AlcedoAndroid. These mirror the desktop C++ domain types
 * (image, project, sleeve filesystem, filters, edit pipeline) and are persisted
 * via Room/DuckDB and serialised into .alcd project packages.
 */

@Serializable
data class ImageItem(
    val id: String,
    val sleevePath: String,
    val originalUri: String,
    val displayName: String,
    val fileExtension: String,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedEpoch: Long,
    val dateCapturedEpoch: Long,
    val rating: Int = 0,
    val flag: ImageFlag = ImageFlag.NONE,
    val colorLabel: ColorLabel = ColorLabel.NONE,
    val isRaw: Boolean = false,
    val isVirtualCopy: Boolean = false,
    val parentId: String? = null,
    val thumbnailPath: String? = null,
    val currentVersionId: String? = null,
    val aiCaption: String? = null,
    val aiTags: List<String> = emptyList(),
    val aiScore: Float? = null,
    val isHidden: Boolean = false,
    // EXIF (denormalised for filtering / inspector)
    val lensModel: String? = null,
    val cameraModel: String? = null,
    val focalLength: Float? = null,
    val iso: Int? = null,
    val aperture: Float? = null,
    val shutterSpeed: String? = null,
)

@Serializable
enum class ImageFlag { NONE, PICK, REJECT }

@Serializable
enum class ColorLabel(val hex: Long) {
    NONE(0x00000000),
    RED(0xFFFF5C5C),
    YELLOW(0xFFFFD24C),
    GREEN(0xFF4CD08C),
    BLUE(0xFF4A9EFF),
    PURPLE(0xFFB05CFF),
}

@Serializable
data class FilterCombo(
    val folderPath: String? = null,
    val ratingMin: Int = 0,
    val ratingMax: Int = 5,
    val flags: Set<ImageFlag> = emptySet(),
    val colorLabels: Set<ColorLabel> = emptySet(),
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val fileExtensions: Set<String> = emptySet(),
    val lensModel: String? = null,
    val cameraModel: String? = null,
    val focalLengthRange: ClosedFloatingPointRange<Float>? = null,
    val isoRange: ClosedFloatingPointRange<Int>? = null,
    val apertureRange: ClosedFloatingPointRange<Float>? = null,
    val shutterSpeedRange: ClosedFloatingPointRange<Double>? = null,
    val searchText: String? = null,
    val semanticTags: Set<String> = emptySet(),
    val includeHidden: Boolean = false,
) {
    val isEmpty: Boolean
        get() = folderPath == null && ratingMin == 0 && ratingMax == 5 &&
            flags.isEmpty() && colorLabels.isEmpty() && dateFrom == null &&
            dateTo == null && fileExtensions.isEmpty() && lensModel == null &&
            cameraModel == null && focalLengthRange == null && isoRange == null &&
            apertureRange == null && shutterSpeedRange == null &&
            searchText.isNullOrBlank() && semanticTags.isEmpty()
}

@Serializable
data class SortDescriptor(
    val field: SortField,
    val ascending: Boolean = true,
)

@Serializable
enum class SortField {
    DATE_CAPTURED, DATE_ADDED, NAME, RATING, FILE_SIZE, AI_SCORE, FOCAL_LENGTH, ISO, APERTURE
}

@Serializable
data class AdjustmentParams(
    // Basic / tone
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    // White balance
    val temperature: Float = 0f,
    val tint: Float = 0f,
    // Color
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val clarity: Float = 0f,
    val sharpen: Float = 0f,
    // Color wheels (lift/gamma/gain)
    val liftHue: Float = 0f,
    val liftSat: Float = 0f,
    val liftLum: Float = 0f,
    val gammaHue: Float = 0f,
    val gammaSat: Float = 0f,
    val gammaLum: Float = 0f,
    val gainHue: Float = 0f,
    val gainSat: Float = 0f,
    val gainLum: Float = 0f,
    // HSL per-hue
    val hslHueShift: FloatArray = FloatArray(8),
    val hslSaturation: FloatArray = FloatArray(8),
    val hslLuminance: FloatArray = FloatArray(8),
    // Tone curve (Hermite control points in 0..1)
    val toneCurveMaster: List<CurvePoint> = defaultLinearCurve(),
    val toneCurveRed: List<CurvePoint> = defaultLinearCurve(),
    val toneCurveGreen: List<CurvePoint> = defaultLinearCurve(),
    val toneCurveBlue: List<CurvePoint> = defaultLinearCurve(),
    // Geometry
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val rotation: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val perspectiveH: Float = 0f,
    val perspectiveV: Float = 0f,
    // Effects
    val filmGrainAmount: Float = 0f,
    val filmGrainSize: Float = 1f,
    val halationAmount: Float = 0f,
    val lutPath: String? = null,
    val lutIntensity: Float = 1f,
    // Display transform
    val displayTransform: String = "OpenDRT",
    val outputColorSpace: String = "sRGB",
    val displayEotf: String = "sRGB",
    val peakLuminanceNits: Int = 100,
    // Raw decode
    val rawBlackLevel: Int = 0,
    val rawWhitePoint: Int = 16383,
    val rawDemosaic: String = "AHD",
    val rawNoiseReduction: Float = 0f,
    val rawHighlightMethod: String = "Clip",
    val lensProfileEnabled: Boolean = false,
    val lensProfileId: String? = null,
    // Lens correction
    val distortion: Float = 0f,
    val vignetteAmount: Float = 0f,
    val vignetteMidpoint: Float = 50f,
    val chromaAberrationR: Float = 0f,
    val chromaAberrationB: Float = 0f,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdjustmentParams) return false
        return exposure == other.exposure && contrast == other.contrast &&
            highlights == other.highlights && shadows == other.shadows &&
            whites == other.whites && blacks == other.blacks &&
            temperature == other.temperature && tint == other.tint &&
            saturation == other.saturation && vibrance == other.vibrance &&
            clarity == other.clarity && sharpen == other.sharpen &&
            liftHue == other.liftHue && liftSat == other.liftSat && liftLum == other.liftLum &&
            gammaHue == other.gammaHue && gammaSat == other.gammaSat && gammaLum == other.gammaLum &&
            gainHue == other.gainHue && gainSat == other.gainSat && gainLum == other.gainLum &&
            hslHueShift.contentEquals(other.hslHueShift) &&
            hslSaturation.contentEquals(other.hslSaturation) &&
            hslLuminance.contentEquals(other.hslLuminance) &&
            toneCurveMaster == other.toneCurveMaster &&
            toneCurveRed == other.toneCurveRed &&
            toneCurveGreen == other.toneCurveGreen &&
            toneCurveBlue == other.toneCurveBlue &&
            cropLeft == other.cropLeft && cropTop == other.cropTop &&
            cropRight == other.cropRight && cropBottom == other.cropBottom &&
            rotation == other.rotation && flipH == other.flipH && flipV == other.flipV &&
            perspectiveH == other.perspectiveH && perspectiveV == other.perspectiveV &&
            filmGrainAmount == other.filmGrainAmount && filmGrainSize == other.filmGrainSize &&
            halationAmount == other.halationAmount && lutPath == other.lutPath &&
            lutIntensity == other.lutIntensity && displayTransform == other.displayTransform &&
            outputColorSpace == other.outputColorSpace && displayEotf == other.displayEotf &&
            peakLuminanceNits == other.peakLuminanceNits &&
            rawBlackLevel == other.rawBlackLevel && rawWhitePoint == other.rawWhitePoint &&
            rawDemosaic == other.rawDemosaic && rawNoiseReduction == other.rawNoiseReduction &&
            rawHighlightMethod == other.rawHighlightMethod &&
            lensProfileEnabled == other.lensProfileEnabled &&
            lensProfileId == other.lensProfileId &&
            distortion == other.distortion && vignetteAmount == other.vignetteAmount &&
            vignetteMidpoint == other.vignetteMidpoint &&
            chromaAberrationR == other.chromaAberrationR &&
            chromaAberrationB == other.chromaAberrationB
    }

    override fun hashCode(): Int {
        var result = exposure.hashCode()
        result = 31 * result + contrast.hashCode()
        result = 31 * result + highlights.hashCode()
        result = 31 * result + shadows.hashCode()
        result = 31 * result + whites.hashCode()
        result = 31 * result + blacks.hashCode()
        result = 31 * result + temperature.hashCode()
        result = 31 * result + tint.hashCode()
        result = 31 * result + saturation.hashCode()
        result = 31 * result + vibrance.hashCode()
        result = 31 * result + clarity.hashCode()
        result = 31 * result + sharpen.hashCode()
        result = 31 * result + hslHueShift.contentHashCode()
        result = 31 * result + hslSaturation.contentHashCode()
        result = 31 * result + hslLuminance.contentHashCode()
        result = 31 * result + toneCurveMaster.hashCode()
        result = 31 * result + lutPath.hashCode()
        return result
    }

    companion object {
        fun defaultLinearCurve(): List<CurvePoint> = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(1f, 1f),
        )

        val DEFAULT = AdjustmentParams()
    }
}

@Serializable
data class CurvePoint(val x: Float, val y: Float)

@Serializable
data class PipelinePreset(
    val id: String,
    val name: String,
    val category: String,
    val adjustments: AdjustmentParams,
    val isBuiltIn: Boolean = false,
    val isFavorite: Boolean = false,
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class WatermarkConfig(
    val enabled: Boolean = false,
    val text: String = "© Alcedo",
    val imagePath: String? = null,
    val opacity: Float = 0.8f,
    val scale: Float = 0.1f,
    val position: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    val fontSize: Float = 24f,
)

@Serializable
enum class WatermarkPosition {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

@Serializable
data class ExportConfig(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    val maxDimension: Int = 0,
    val colorSpace: String = "sRGB",
    val includeMetadata: Boolean = true,
    val includeWatermark: Boolean = false,
    val watermark: WatermarkConfig = WatermarkConfig(),
    val ultraHdr: Boolean = false,
    val outputDirectory: String? = null,
    val namingPattern: String = "{name}_edit",
    val bitDepth: Int = 8,
    val metaMode: String = "KEEP_ALL",
    val maintainAspect: Boolean = true,
    val resizeWidth: Int = 0,
    val resizeHeight: Int = 0,
    val iccProfile: String = "sRGB IEC61966-2.1",
    /** When the requested format is unsupported on-device, the file is written
     *  with this extension instead (e.g. TIFF degrades to PNG). */
    val fallbackExtension: String? = null,
)

@Serializable
enum class ExportFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    TIFF("tif", "image/tiff"),
    WEBP("webp", "image/webp");

    companion object {
        fun fromExtension(ext: String): ExportFormat? =
            values().firstOrNull { it.extension.equals(ext, ignoreCase = true) }
    }
}

/** Snapshot of a single background task's progress for the UI task bar. */
data class BackgroundTaskInfo(
    val id: String,
    val type: BackgroundTaskType,
    val title: String,
    val progress: Float,
    val indeterminate: Boolean = false,
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val etaMs: Long? = null,
    val error: String? = null,
    val cancellable: Boolean = true,
)

enum class BackgroundTaskType {
    IMPORT, THUMBNAIL, EXPORT, AI_EMBEDDING, AI_RATING, MODEL_DOWNLOAD, BATCH_EDIT, PROJECT_PACKAGE
}
