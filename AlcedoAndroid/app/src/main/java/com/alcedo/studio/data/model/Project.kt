package com.alcedo.studio.data.model

import kotlinx.serialization.json.JsonObject
import java.time.Instant

data class Project(
    val projectId: String,
    val projectName: String,
    val projectPath: String,
    val sleeveRootId: UInt = 0u,
    val createdAt: Instant = Instant.now(),
    val modifiedAt: Instant = Instant.now(),
    val metadata: JsonObject? = null,
    val thumbnailCachePath: String = "",
    val modelCachePath: String = ""
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
    val isHdr: Boolean = false,
    val outputPath: String = ""
)

enum class ExportFormat {
    JPEG, PNG, TIFF, EXR, ULTRA_HDR
}

enum class ColorSpace {
    SRGB, DISPLAY_P3, REC2020, ACES, CUSTOM
}

data class PipelineParams(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whiteBalanceTemp: Float = 6500f,
    val whiteBalanceTint: Float = 0f,
    val toneCurveX: FloatArray = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val toneCurveY: FloatArray = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val geometryRotate: Float = 0f,
    val geometryCropLeft: Float = 0f,
    val geometryCropTop: Float = 0f,
    val geometryCropRight: Float = 1f,
    val geometryCropBottom: Float = 1f,
    val filmGrainIntensity: Float = 0f,
    val halationIntensity: Float = 0f,
    val lutPath: String = "",
    val sharpenAmount: Float = 0f,
    val clarityAmount: Float = 0f,
    val displayTransform: DisplayTransform = DisplayTransform(),
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
    val useCameraMatrix: Boolean = true
)

enum class DemosaicAlgorithm {
    RCD, AMAZE, DCB, SIMPLE
}
