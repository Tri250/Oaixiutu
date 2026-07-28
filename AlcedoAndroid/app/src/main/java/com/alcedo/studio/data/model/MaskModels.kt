package com.alcedo.studio.data.model

import kotlinx.serialization.Serializable

/**
 * Mask models. Masks drive local adjustments (brush, radial, gradient, AI
 * subject/background) and are part of the non-destructive edit history. Each
 * mask references a version and is rendered by the GPU pipeline as an alpha
 * coverage map before the masked operators run.
 */
@Serializable
sealed class Mask {
    abstract val id: String
    abstract val versionId: String
    abstract val name: String
    abstract val enabled: Boolean
    abstract val opacity: Float
    abstract val invert: Boolean
    abstract val feather: Float
    abstract val adjustments: AdjustmentParamsDelta

    abstract val kind: MaskKind
}

@Serializable
enum class MaskKind {
    BRUSH, RADIAL, LINEAR_GRADIENT, RANGE_LUMINANCE, RANGE_COLOR, SUBJECT, BACKGROUND, SKY, OBJECT
}

@Serializable
data class BrushMask(
    override val id: String,
    override val versionId: String,
    override val name: String,
    override val enabled: Boolean = true,
    override val opacity: Float = 1f,
    override val invert: Boolean = false,
    override val feather: Float = 0f,
    override val adjustments: AdjustmentParamsDelta = AdjustmentParamsDelta(),
    val strokes: List<BrushStroke> = emptyList(),
) : Mask() {
    override val kind: MaskKind = MaskKind.BRUSH
}

@Serializable
data class BrushStroke(
    val points: List<FloatArray>,
    val radius: Float,
    val hardness: Float,
    val flow: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrushStroke) return false
        return radius == other.radius && hardness == other.hardness &&
            flow == other.flow && points.size == other.points.size &&
            points.indices.all { i -> points[i].contentEquals(other.points[i]) }
    }

    override fun hashCode(): Int {
        var r = radius.hashCode()
        r = 31 * r + hardness.hashCode()
        r = 31 * r + flow.hashCode()
        return r
    }
}

@Serializable
data class RadialMask(
    override val id: String,
    override val versionId: String,
    override val name: String,
    override val enabled: Boolean = true,
    override val opacity: Float = 1f,
    override val invert: Boolean = false,
    override val feather: Float = 0.5f,
    override val adjustments: AdjustmentParamsDelta = AdjustmentParamsDelta(),
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val rotation: Float = 0f,
) : Mask() {
    override val kind: MaskKind = MaskKind.RADIAL
}

@Serializable
data class LinearGradientMask(
    override val id: String,
    override val versionId: String,
    override val name: String,
    override val enabled: Boolean = true,
    override val opacity: Float = 1f,
    override val invert: Boolean = false,
    override val feather: Float = 0.5f,
    override val adjustments: AdjustmentParamsDelta = AdjustmentParamsDelta(),
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
) : Mask() {
    override val kind: MaskKind = MaskKind.LINEAR_GRADIENT
}

@Serializable
data class LuminanceRangeMask(
    override val id: String,
    override val versionId: String,
    override val name: String,
    override val enabled: Boolean = true,
    override val opacity: Float = 1f,
    override val invert: Boolean = false,
    override val feather: Float = 0.2f,
    override val adjustments: AdjustmentParamsDelta = AdjustmentParamsDelta(),
    val luminanceMin: Float,
    val luminanceMax: Float,
) : Mask() {
    override val kind: MaskKind = MaskKind.RANGE_LUMINANCE
}

@Serializable
data class ColorRangeMask(
    override val id: String,
    override val versionId: String,
    override val name: String,
    override val enabled: Boolean = true,
    override val opacity: Float = 1f,
    override val invert: Boolean = false,
    override val feather: Float = 0.2f,
    override val adjustments: AdjustmentParamsDelta = AdjustmentParamsDelta(),
    val centerHue: Float,
    val hueRange: Float,
    val saturationMin: Float,
) : Mask() {
    override val kind: MaskKind = MaskKind.RANGE_COLOR
}

@Serializable
data class AiSubjectMask(
    override val id: String,
    override val versionId: String,
    override val name: String,
    override val enabled: Boolean = true,
    override val opacity: Float = 1f,
    override val invert: Boolean = false,
    override val feather: Float = 0.1f,
    override val adjustments: AdjustmentParamsDelta = AdjustmentParamsDelta(),
    val subjectKind: AiSubjectKind,
    val coveragePath: String?,
    val polygon: List<FloatArray> = emptyList(),
) : Mask() {
    override val kind: MaskKind = when (subjectKind) {
        AiSubjectKind.SUBJECT -> MaskKind.SUBJECT
        AiSubjectKind.BACKGROUND -> MaskKind.BACKGROUND
        AiSubjectKind.SKY -> MaskKind.SKY
        AiSubjectKind.OBJECT -> MaskKind.OBJECT
    }
}

@Serializable
enum class AiSubjectKind { SUBJECT, BACKGROUND, SKY, OBJECT }

/** A flat representation used when persisting masks to Room. */
@Serializable
data class MaskRecord(
    val id: String,
    val versionId: String,
    val name: String,
    val kind: MaskKind,
    val enabled: Boolean,
    val opacity: Float,
    val invert: Boolean,
    val feather: Float,
    val serialisedPayload: String,
)
