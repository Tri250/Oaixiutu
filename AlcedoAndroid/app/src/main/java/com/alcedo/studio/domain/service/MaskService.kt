package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.AiSubjectKind
import com.alcedo.studio.data.model.BrushMask
import com.alcedo.studio.data.model.ColorRangeMask
import com.alcedo.studio.data.model.LuminanceRangeMask
import com.alcedo.studio.data.model.Mask
import com.alcedo.studio.data.model.MaskKind
import com.alcedo.studio.data.model.MaskRecord
import com.alcedo.studio.data.model.RadialMask
import com.alcedo.studio.data.model.LinearGradientMask
import com.alcedo.studio.utils.IdGenerator
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mask operations service. Builds, serialises and composes masks for local
 * adjustments. Each mask is attached to a version and rendered as a coverage
 * map by [MaskRenderService] before the masked operators run.
 */
@Singleton
class MaskService @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun newBrushMask(versionId: String, name: String = "Brush"): BrushMask =
        BrushMask(
            id = IdGenerator.newId("mask"),
            versionId = versionId,
            name = name,
        )

    fun newRadialMask(versionId: String, cx: Float, cy: Float, rx: Float, ry: Float, name: String = "Radial"): RadialMask =
        RadialMask(
            id = IdGenerator.newId("mask"),
            versionId = versionId,
            name = name,
            centerX = cx, centerY = cy, radiusX = rx, radiusY = ry,
        )

    fun newLinearMask(versionId: String, sx: Float, sy: Float, ex: Float, ey: Float, name: String = "Linear"): LinearGradientMask =
        LinearGradientMask(
            id = IdGenerator.newId("mask"),
            versionId = versionId,
            name = name,
            startX = sx, startY = sy, endX = ex, endY = ey,
        )

    fun newLuminanceMask(versionId: String, min: Float, max: Float, name: String = "Luma"): LuminanceRangeMask =
        LuminanceRangeMask(
            id = IdGenerator.newId("mask"),
            versionId = versionId,
            name = name,
            luminanceMin = min, luminanceMax = max,
        )

    fun newColorMask(versionId: String, hue: Float, range: Float, name: String = "Color"): ColorRangeMask =
        ColorRangeMask(
            id = IdGenerator.newId("mask"),
            versionId = versionId,
            name = name,
            centerHue = hue, hueRange = range, saturationMin = 0.1f,
        )

    fun newSubjectMask(versionId: String, kind: AiSubjectKind, name: String = "Subject"): com.alcedo.studio.data.model.AiSubjectMask =
        com.alcedo.studio.data.model.AiSubjectMask(
            id = IdGenerator.newId("mask"),
            versionId = versionId,
            name = name,
            subjectKind = kind,
            coveragePath = null,
        )

    /** Serialise a [Mask] to the flat [MaskRecord] for persistence. */
    fun toRecord(mask: Mask): MaskRecord = MaskRecord(
        id = mask.id,
        versionId = mask.versionId,
        name = mask.name,
        kind = mask.kind,
        enabled = mask.enabled,
        opacity = mask.opacity,
        invert = mask.invert,
        feather = mask.feather,
        serialisedPayload = json.encodeToString(mask),
    )

    /** Toggle a mask's enabled state, returning a copy. */
    fun toggle(mask: Mask): Mask = when (mask) {
        is BrushMask -> mask.copy(enabled = !mask.enabled)
        is RadialMask -> mask.copy(enabled = !mask.enabled)
        is LinearGradientMask -> mask.copy(enabled = !mask.enabled)
        is LuminanceRangeMask -> mask.copy(enabled = !mask.enabled)
        is ColorRangeMask -> mask.copy(enabled = !mask.enabled)
        is com.alcedo.studio.data.model.AiSubjectMask -> mask.copy(enabled = !mask.enabled)
    }

    /** Set the opacity on a mask, returning a copy. */
    fun withOpacity(mask: Mask, opacity: Float): Mask {
        val o = opacity.coerceIn(0f, 1f)
        return when (mask) {
            is BrushMask -> mask.copy(opacity = o)
            is RadialMask -> mask.copy(opacity = o)
            is LinearGradientMask -> mask.copy(opacity = o)
            is LuminanceRangeMask -> mask.copy(opacity = o)
            is ColorRangeMask -> mask.copy(opacity = o)
            is com.alcedo.studio.data.model.AiSubjectMask -> mask.copy(opacity = o)
        }
    }

    /** Human-readable label for a mask kind. */
    fun labelFor(kind: MaskKind): String = when (kind) {
        MaskKind.BRUSH -> "Brush"
        MaskKind.RADIAL -> "Radial"
        MaskKind.LINEAR_GRADIENT -> "Linear Gradient"
        MaskKind.RANGE_LUMINANCE -> "Luminance Range"
        MaskKind.RANGE_COLOR -> "Color Range"
        MaskKind.SUBJECT -> "Subject (AI)"
        MaskKind.BACKGROUND -> "Background (AI)"
        MaskKind.SKY -> "Sky (AI)"
        MaskKind.OBJECT -> "Object (AI)"
    }
}
