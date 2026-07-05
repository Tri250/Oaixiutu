package com.alcedo.studio.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import java.time.Instant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

@Serializable
data class Project(
    val projectId: String,
    val projectName: String,
    val projectPath: String,
    val sleeveRootId: UInt = 0u,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val modifiedAt: Instant = Instant.now(),
    val metadata: JsonObject? = null,
    val thumbnailCachePath: String = "",
    val modelCachePath: String = "",
    val packageVersion: Int = CURRENT_PACKAGE_VERSION,
    @Serializable(with = InstantSerializer::class) val lastAutoSaveTime: Instant = Instant.now(),
    val isOpen: Boolean = false,
    val projectFileSize: Long = 0L
) {
    companion object {
        const val CURRENT_PACKAGE_VERSION = 1
        const val FILE_EXTENSION = ".alcd"
    }
}

@Serializable
data class RecentProject(
    val projectId: String,
    val projectName: String,
    val projectPath: String,
    val lastOpenedAt: Instant = Instant.now(),
    val thumbnailPath: String = ""
)

@Serializable
data class ProjectPackage(
    val version: Int = Project.CURRENT_PACKAGE_VERSION,
    val project: Project,
    val sleeveTree: SleeveTreeSnapshot = SleeveTreeSnapshot(),
    val editHistories: Map<UInt, EditHistory> = emptyMap(),
    val imageMetadata: List<ImageMetaSnapshot> = emptyList(),
    val embeddedAssets: List<EmbeddedAsset> = emptyList(),
    val createdAt: Instant = Instant.now()
)

@Serializable
data class SleeveTreeSnapshot(
    val rootId: Long = 0L,
    val elements: List<SleeveElementSnapshot> = emptyList()
)

@Serializable
data class SleeveElementSnapshot(
    val elementId: Long,
    val elementName: String,
    val elementType: String, // "FILE" or "FOLDER"
    val parentId: Long?,
    val imageId: Long? = null,
    val currentVersionId: String? = null
)

@Serializable
data class ImageMetaSnapshot(
    val imageId: UInt,
    val imagePath: String,
    val imageName: String,
    val imageType: String,
    val checksum: ULong = 0u,
    val exifDisplay: ExifDisplayMetaData = ExifDisplayMetaData()
)

@Serializable
data class EmbeddedAsset(
    val assetId: String,
    val assetType: AssetType,
    val data: ByteArray = byteArrayOf(),
    val mimeType: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddedAsset) return false
        return assetId == other.assetId && assetType == other.assetType && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = assetId.hashCode()
        result = 31 * result + assetType.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

enum class AssetType {
    THUMBNAIL, PREVIEW, LUT, ICC_PROFILE, SIDE_CAR
}

@Serializable
data class ProjectBackup(
    val backupId: String,
    val projectId: String,
    val projectName: String,
    val backupPath: String,
    val createdAt: Instant = Instant.now(),
    val packageVersion: Int = 1,
    val fileSize: Long = 0L
)

@Serializable
data class PackageMigration(
    val fromVersion: Int,
    val toVersion: Int,
    val appliedAt: Instant = Instant.now()
)

enum class ProjectValidationError {
    MISSING_FILE, CORRUPTED_DATA, INCOMPATIBLE_VERSION,
    MISSING_SLEEVE_ROOT, ORPHANED_REFERENCES, UNKNOWN
}

@Serializable
data class ProjectValidation(
    val isValid: Boolean,
    val errors: List<ProjectValidationError> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
data class ImageAdjustment(
    val imageId: UInt,
    val pipelineParams: PipelineParams = PipelineParams(),
    val displayTransform: DisplayTransform = DisplayTransform(),
    val rawDecodeParams: RawDecodeParams = RawDecodeParams()
)

@Serializable
data class TransferPreview(
    val sourceImageId: UInt,
    val targetImageId: UInt,
    val previewPath: String = "",
    val paramsDiff: Map<String, Pair<Float, Float>> = emptyMap()
)

enum class TransferParamGroup {
    EXPOSURE, COLOR, TONE_CURVE, GEOMETRY, DETAIL,
    WHITE_BALANCE, HSL, COLOR_WHEEL, TINT,
    LENS, FILM_GRAIN, HALATION, SHARPEN, CLARITY,
    DISPLAY, RAW_DECODE, ALL
}

@Serializable
data class TransferConfig(
    val enabledGroups: Set<TransferParamGroup> = setOf(TransferParamGroup.ALL),
    val copyGeometry: Boolean = true,
    val copyDisplayTransform: Boolean = false,
    val copyRawDecodeParams: Boolean = false
)

data class AiModelProfile(
    val modelId: String,
    val modelName: String,
    val modelType: AiModelType,
    val localPath: String? = null,
    val downloadUrl: String? = null,
    val isDownloaded: Boolean = false,
    val isActive: Boolean = false,
    val version: String = "",
    val description: String = ""
)

enum class AiModelType {
    CLIP, SIGLIP, VLM, COREML, CUSTOM
}

data class SemanticLabel(
    val labelId: String,
    val imageId: UInt,
    val label: String,
    val confidence: Float,
    val modelId: String,
    val generatedAt: Instant = Instant.now()
)

data class VectorIndexEntry(
    val imageId: UInt,
    val embedding: FloatArray,
    val modelId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorIndexEntry) return false
        return imageId == other.imageId && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = imageId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

data class SearchResult(
    val imageId: UInt,
    val score: Float,
    val resultType: ResultType
)

enum class ResultType {
    EXACT, SEMANTIC, EXIF, LABEL
}

data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 95,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
    val embedIcc: Boolean = true,
    val includeMetadata: Boolean = true,
    val maxDimension: Int? = null,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val isHdr: Boolean = false,
    val bitDepth: Int = 8,
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val outputPath: String = "",
    val sourceExifPath: String? = null
)

enum class ExportFormat {
    JPEG, PNG, TIFF, EXR, ULTRA_HDR
}

enum class ColorSpace {
    SRGB, DISPLAY_P3, REC2020, ACES, CUSTOM
}

// ================================================================
// Full Pipeline Parameters
// ================================================================

data class PipelineParams(
    // Basic adjustments
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val midtones: Float = 0f,
    val shadowBoundary: Float = 0.25f,
    val highlightBoundary: Float = 0.75f,
    val whiteBalanceTemp: Float = 6500f,
    val whiteBalanceTint: Float = 0f,

    // Auto exposure
    val autoExposureEnabled: Boolean = false,
    val autoExposureTargetPercentile: Float = 0.5f,
    val autoExposureTargetLuminance: Float = 0.18f,
    val autoExposureValue: Float = 0f, // computed result in EV

    // Tone curve
    val toneCurveX: FloatArray = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val toneCurveY: FloatArray = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val toneCurvePoints: Int = 5,

    // Sigmoid contrast
    val sigmoidContrast: Float = 0f,
    val sigmoidPivot: Float = 0.18f,
    val sigmoidShoulder: Float = 0.5f,

    // Tint (split toning)
    val tintHighlightHue: Float = 0f,
    val tintHighlightStrength: Float = 0f,
    val tintShadowHue: Float = 0f,
    val tintShadowStrength: Float = 0f,
    val tintBalance: Float = 0f,

    // Color wheels (CDL)
    val colorWheelLiftR: Float = 0f,
    val colorWheelLiftG: Float = 0f,
    val colorWheelLiftB: Float = 0f,
    val colorWheelGammaR: Float = 1f,
    val colorWheelGammaG: Float = 1f,
    val colorWheelGammaB: Float = 1f,
    val colorWheelGainR: Float = 1f,
    val colorWheelGainG: Float = 1f,
    val colorWheelGainB: Float = 1f,

    // HSL
    val hslHueRanges: FloatArray = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f),
    val hslHueWidth: Float = 60f,
    val hslHueShift: FloatArray = FloatArray(8) { 0f },
    val hslSaturationScale: FloatArray = FloatArray(8) { 1f },
    val hslLuminanceScale: FloatArray = FloatArray(8) { 1f },

    // Channel mixer
    val channelMixerMatrix: FloatArray = floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f),
    val channelMixerMonochrome: Boolean = false,

    // Clarity
    val clarityAmount: Float = 0f,
    val clarityRadius: Float = 15f,

    // Sharpen
    val sharpenAmount: Float = 0f,

    // Film grain
    val filmGrainIntensity: Float = 0f,

    // Halation
    val halationIntensity: Float = 0f,
    val halationThreshold: Float = 0.8f,
    val halationSpread: Float = 10f,
    val halationRedBias: Float = 0.7f,

    // LUT
    val lutPath: String = "",
    val lutEnabled: Boolean = false,

    // Geometry
    val geometryRotate: Float = 0f,
    val geometryScale: Float = 1f,
    val geometryCropLeft: Float = 0f,
    val geometryCropTop: Float = 0f,
    val geometryCropRight: Float = 1f,
    val geometryCropBottom: Float = 1f,
    val geometryPerspectiveSrc: FloatArray = floatArrayOf(0f,0f, 1f,0f, 1f,1f, 0f,1f),
    val geometryPerspectiveDst: FloatArray = floatArrayOf(0f,0f, 1f,0f, 1f,1f, 0f,1f),

    // Lens correction
    val lensK1: Float = 0f,
    val lensK2: Float = 0f,
    val lensK3: Float = 0f,
    val lensP1: Float = 0f,
    val lensP2: Float = 0f,
    val lensCx: Float = 0.5f,
    val lensCy: Float = 0.5f,
    val lensFocalRatio: Float = 1f,
    val lensVignetteStrength: Float = 0f,

    // DNG warp
    val dngWarpCoeffs: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),

    // Display transform
    val displayTransform: DisplayTransform = DisplayTransform(),

    // RAW decode
    val rawDecodeParams: RawDecodeParams = RawDecodeParams()
)

data class DisplayTransform(
    val colorScience: ColorScience = ColorScience.ACES20,
    val eotf: EOTF = EOTF.SRGB,
    val peakLuminance: Float = 100f,
    val displayColorSpace: ColorSpace = ColorSpace.SRGB
)

enum class ColorScience {
    ACES20, OPENDRT, LINEAR
}

enum class EOTF {
    SRGB, PQ, HLG, GAMMA22, GAMMA24
}

data class RawDecodeParams(
    val demosaicAlgorithm: DemosaicAlgorithm = DemosaicAlgorithm.RCD,
    val highlightReconstruction: Boolean = true,
    val autoBrightness: Boolean = false,
    val useCameraMatrix: Boolean = true,
    val bayerPattern: Int = 0,  // 0=RGGB, 1=BGGR, 2=GRBG, 3=GBRG
    val whiteLevel: Int = 65535,
    val blackLevel: Int = 0
)

enum class DemosaicAlgorithm {
    RCD, AHD, AMAZE, DCB, SIMPLE
}

// ================================================================
// Search Query Classification
// ================================================================

enum class QueryType {
    EXACT, EXIF, LABEL, SEMANTIC
}

@Serializable
data class QueryClassification(
    val rawQuery: String,
    val classifiedType: QueryType = QueryType.EXACT,
    val tokens: List<String> = emptyList(),
    val confidence: Float = 0f,
    val isExactName: Boolean = false,
    val isFreeText: Boolean = true,
    val isExifQuery: Boolean = false,
    val isLabelQuery: Boolean = false,
    val isSemanticQuery: Boolean = false,
    val exifCameraMake: Boolean = false,
    val exifCameraModel: Boolean = false,
    val exifLensModel: Boolean = false,
    val exifFocalLength: Boolean = false,
    val exifAperture: Boolean = false,
    val exifIso: Boolean = false,
    val exifCaptureDate: Boolean = false
)