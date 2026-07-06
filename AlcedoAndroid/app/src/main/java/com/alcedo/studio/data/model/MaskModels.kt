package com.alcedo.studio.data.model

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset

/**
 * AI Mask system data model for local adjustments, inspired by RapidRAW.
 *
 * A [MaskContainer] holds a list of [SubMask]s that are combined with
 * [MaskCombineMode]s and a set of [MaskAdjustments] applied to the masked
 * region of the original image.
 */

/**
 * 单条画笔笔触。所有坐标均使用相对于图片的归一化坐标 (0-1)，
 * 这样笔触在不同显示尺寸下都保持正确的相对位置。
 */
data class BrushStroke(
    val points: List<Offset> = emptyList(),  // 笔触点序列（归一化坐标 0-1）
    val size: Float = 0.05f,                  // 画笔大小（归一化）
    val hardness: Float = 0.5f,               // 硬度 0-1（边缘羽化）
    val opacity: Float = 1f,                  // 不透明度 0-1
    val isEraser: Boolean = false             // 是否为橡皮擦
)

/** Supported mask types (AI + manual geometric + range-based). */
enum class MaskType {
    SUBJECT,
    SKY,
    FOREGROUND,
    LINEAR,
    RADIAL,
    BRUSH,
    COLOR_RANGE,
    LUMINANCE_RANGE,
    WHOLE_IMAGE
}

/** How a sub-mask is combined with the accumulated mask. */
enum class MaskCombineMode {
    ADDITIVE,
    SUBTRACTIVE,
    INTERSECT
}

/** A single user-drawn brush stroke sample. */
data class BrushPoint(
    val x: Float,
    val y: Float,
    val pressure: Float
)

/**
 * Parameters specific to each [MaskType].
 *
 * Only the fields relevant to a given mask type are populated; the rest
 * are null. This keeps the model flat and serializable.
 */
data class MaskParams(
    val brushPoints: List<BrushPoint>? = null,
    val linearStart: PointF? = null,
    val linearEnd: PointF? = null,
    val radialCenter: PointF? = null,
    val radialRadius: Float = 0f,
    val colorTarget: Int? = null,
    val colorRange: Float = 0.1f,
    val luminanceMin: Float = 0f,
    val luminanceMax: Float = 1f,
    val feather: Float = 0.2f,
    val brushSize: Float = 0.05f,
    val brushHardness: Float = 0.5f,
    val brushOpacity: Float = 1f,
    val brushStrokes: List<BrushStroke> = emptyList()
) {
    companion object {
        fun defaultFor(type: MaskType): MaskParams = when (type) {
            MaskType.SUBJECT, MaskType.SKY, MaskType.FOREGROUND, MaskType.WHOLE_IMAGE -> MaskParams()
            MaskType.LINEAR -> MaskParams(
                linearStart = PointF(0.2f, 0.2f),
                linearEnd = PointF(0.8f, 0.8f),
                feather = 0.3f
            )
            MaskType.RADIAL -> MaskParams(
                radialCenter = PointF(0.5f, 0.5f),
                radialRadius = 0.4f,
                feather = 0.3f
            )
            MaskType.BRUSH -> MaskParams(
                brushPoints = emptyList(),
                brushSize = 0.05f,
                brushHardness = 0.5f,
                brushOpacity = 1f,
                brushStrokes = emptyList()
            )
            MaskType.COLOR_RANGE -> MaskParams(
                colorTarget = 0x808080,
                colorRange = 0.15f
            )
            MaskType.LUMINANCE_RANGE -> MaskParams(
                luminanceMin = 0.25f,
                luminanceMax = 0.75f
            )
        }
    }
}

/** A single mask layer inside a [MaskContainer]. */
data class SubMask(
    val id: String,
    val type: MaskType,
    val combineMode: MaskCombineMode,
    val visible: Boolean = true,
    val inverted: Boolean = false,
    val opacity: Float = 1f,
    val name: String = "",
    val params: MaskParams
)

/** Local adjustments applied to the masked region. */
data class MaskAdjustments(
    var exposure: Float = 0f,
    var contrast: Float = 0f,
    var highlights: Float = 0f,
    var shadows: Float = 0f,
    var saturation: Float = 0f,
    var temperature: Float = 0f,
    var tint: Float = 0f,
    var clarity: Float = 0f,
    var sharpen: Float = 0f
)

/**
 * A mask container groups one or more [SubMask]s with a set of
 * [MaskAdjustments]. Multiple containers can be stacked; each one
 * independently adjusts its own masked region.
 */
data class MaskContainer(
    val id: String,
    val name: String,
    val subMasks: List<SubMask>,
    val visible: Boolean = true,
    val inverted: Boolean = false,
    val opacity: Float = 1f,
    val adjustments: MaskAdjustments = MaskAdjustments()
)
