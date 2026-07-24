package com.alcedo.studio.data.preset

import com.alcedo.studio.data.model.PipelineParams

/**
 * A built-in preset definition with i18n names, category, and parameter overrides.
 * Built-in presets are available immediately without database access.
 */
data class BuiltinPreset(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val category: PresetCategory,
    val thumbnail: String = "",
    val params: PipelineParams
)

/**
 * Preset categories for organizing built-in and user presets.
 * Maps to [com.alcedo.studio.domain.service.PresetService] category strings
 * for database compatibility.
 */
enum class PresetCategory(val key: String, val labelZh: String, val labelEn: String) {
    Basic("General", "基础调整", "Basic"),
    Film("Film", "胶片模拟", "Film"),
    Portrait("Portrait", "人像美化", "Portrait"),
    Landscape("Landscape", "风景增强", "Landscape"),
    Street("Street", "街拍风格", "Street"),
    Vintage("Vintage", "复古怀旧", "Vintage"),
    BnW("B&W", "黑白", "B&W"),
    Creative("Creative", "创意风格", "Creative")
}

/**
 * Comprehensive built-in preset library (32 presets, 4 per category).
 * Each preset sets realistic parameter values for the Alcedo pipeline.
 */
object BuiltinPresets {

    val all: List<BuiltinPreset> by lazy {
        basicPresets + filmPresets + portraitPresets + landscapePresets +
            streetPresets + vintagePresets + bnwPresets + creativePresets
    }

    fun getByCategory(category: PresetCategory): List<BuiltinPreset> =
        all.filter { it.category == category }

    // ═══════════════════════════════════════════════════════════════
    // 基础调整 / Basic
    // ═══════════════════════════════════════════════════════════════

    private val basicPresets = listOf(
        BuiltinPreset(
            id = "basic_auto_enhance",
            nameZh = "自动增强",
            nameEn = "Auto Enhance",
            category = PresetCategory.Basic,
            params = PipelineParams(
                exposure = 0.08f,
                contrast = 0.12f,
                saturation = 0.08f,
                vibrance = 0.12f,
                highlights = -0.1f,
                shadows = 0.1f,
                clarityAmount = 0.1f,
                dehazeAmount = 0.08f,
                sharpenAmount = 0.08f
            )
        ),
        BuiltinPreset(
            id = "basic_warm_tone",
            nameZh = "暖色调",
            nameEn = "Warm Tone",
            category = PresetCategory.Basic,
            params = PipelineParams(
                whiteBalanceTemp = 7000f,
                whiteBalanceTint = 8f,
                exposure = 0.05f,
                saturation = 0.1f,
                vibrance = 0.08f,
                tintHighlightHue = 30f,
                tintHighlightStrength = 0.1f
            )
        ),
        BuiltinPreset(
            id = "basic_cool_tone",
            nameZh = "冷色调",
            nameEn = "Cool Tone",
            category = PresetCategory.Basic,
            params = PipelineParams(
                whiteBalanceTemp = 5500f,
                whiteBalanceTint = -6f,
                contrast = 0.08f,
                saturation = -0.05f,
                tintShadowHue = 200f,
                tintShadowStrength = 0.08f
            )
        ),
        BuiltinPreset(
            id = "basic_high_key",
            nameZh = "高调",
            nameEn = "High Key",
            category = PresetCategory.Basic,
            params = PipelineParams(
                exposure = 0.3f,
                contrast = -0.15f,
                highlights = 0.15f,
                shadows = 0.25f,
                saturation = -0.08f,
                toneCurveY = floatArrayOf(0.05f, 0.3f, 0.55f, 0.78f, 1f)
            )
        ),
        BuiltinPreset(
            id = "basic_low_key",
            nameZh = "低调",
            nameEn = "Low Key",
            category = PresetCategory.Basic,
            params = PipelineParams(
                exposure = -0.25f,
                contrast = 0.2f,
                highlights = -0.15f,
                shadows = -0.2f,
                saturation = -0.1f,
                toneCurveY = floatArrayOf(0f, 0.15f, 0.38f, 0.6f, 0.9f)
            )
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // 胶片模拟 / Film
    // ═══════════════════════════════════════════════════════════════

    private val filmPresets = listOf(
        BuiltinPreset(
            id = "film_portra_400",
            nameZh = "Portra 400",
            nameEn = "Portra 400",
            category = PresetCategory.Film,
            params = PipelineParams(
                contrast = -0.08f,
                saturation = -0.1f,
                vibrance = 0.05f,
                shadows = 0.12f,
                highlights = -0.08f,
                whiteBalanceTemp = 6800f,
                whiteBalanceTint = 5f,
                filmGrainIntensity = 0.12f,
                halationIntensity = 0.12f,
                halationThreshold = 0.85f,
                halationRedBias = 0.65f,
                tintHighlightHue = 35f,
                tintHighlightStrength = 0.08f,
                hslSaturationScale = floatArrayOf(1.0f, 1.05f, 1.1f, 0.95f, 0.9f, 1.0f, 1.0f, 1.0f),
                hslLuminanceScale = floatArrayOf(1.0f, 0.98f, 1.0f, 1.02f, 1.05f, 1.0f, 1.0f, 1.0f)
            )
        ),
        BuiltinPreset(
            id = "film_velvia_50",
            nameZh = "Velvia 50",
            nameEn = "Velvia 50",
            category = PresetCategory.Film,
            params = PipelineParams(
                contrast = 0.2f,
                saturation = 0.35f,
                vibrance = 0.25f,
                shadows = -0.05f,
                highlights = -0.1f,
                whiteBalanceTemp = 6200f,
                clarityAmount = 0.12f,
                sharpenAmount = 0.1f,
                hslSaturationScale = floatArrayOf(1.1f, 1.2f, 1.15f, 1.3f, 1.1f, 1.0f, 1.0f, 1.05f),
                hslLuminanceScale = floatArrayOf(1.0f, 0.95f, 1.0f, 0.92f, 1.0f, 1.0f, 1.0f, 1.0f)
            )
        ),
        BuiltinPreset(
            id = "film_trix_400",
            nameZh = "Tri-X 400",
            nameEn = "Tri-X 400",
            category = PresetCategory.Film,
            params = PipelineParams(
                contrast = 0.3f,
                saturation = -1f,
                vibrance = -1f,
                shadows = 0.05f,
                highlights = -0.15f,
                channelMixerMonochrome = true,
                channelMixerMatrix = floatArrayOf(
                    0.35f, 0.55f, 0.1f,
                    0.35f, 0.55f, 0.1f,
                    0.35f, 0.55f, 0.1f
                ),
                filmGrainIntensity = 0.25f,
                clarityAmount = 0.15f,
                lensVignetteStrength = 0.15f
            )
        ),
        BuiltinPreset(
            id = "film_cinestill_800t",
            nameZh = "CineStill 800T",
            nameEn = "CineStill 800T",
            category = PresetCategory.Film,
            params = PipelineParams(
                contrast = 0.12f,
                saturation = 0.05f,
                vibrance = 0.1f,
                whiteBalanceTemp = 5200f,
                whiteBalanceTint = -5f,
                exposure = 0.05f,
                tintHighlightHue = 25f,
                tintHighlightStrength = 0.15f,
                tintShadowHue = 215f,
                tintShadowStrength = 0.2f,
                halationIntensity = 0.35f,
                halationThreshold = 0.82f,
                halationRedBias = 0.75f,
                filmGrainIntensity = 0.15f,
                lensVignetteStrength = 0.1f
            )
        ),
        BuiltinPreset(
            id = "film_ektachrome",
            nameZh = "Ektachrome",
            nameEn = "Ektachrome",
            category = PresetCategory.Film,
            params = PipelineParams(
                contrast = 0.15f,
                saturation = 0.2f,
                vibrance = 0.15f,
                shadows = -0.05f,
                highlights = -0.12f,
                whiteBalanceTemp = 6300f,
                whiteBalanceTint = -3f,
                clarityAmount = 0.1f,
                hslSaturationScale = floatArrayOf(1.0f, 1.1f, 1.15f, 1.2f, 1.0f, 1.1f, 1.0f, 1.0f),
                hslLuminanceScale = floatArrayOf(1.0f, 0.97f, 1.0f, 0.95f, 1.0f, 0.98f, 1.0f, 1.0f),
                tintHighlightHue = 40f,
                tintHighlightStrength = 0.06f
            )
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // 人像美化 / Portrait
    // ═══════════════════════════════════════════════════════════════

    private val portraitPresets = listOf(
        BuiltinPreset(
            id = "portrait_soft_skin",
            nameZh = "柔肤",
            nameEn = "Soft Skin",
            category = PresetCategory.Portrait,
            params = PipelineParams(
                contrast = -0.15f,
                saturation = 0.05f,
                vibrance = 0.08f,
                highlights = -0.08f,
                shadows = 0.1f,
                whiteBalanceTemp = 6700f,
                whiteBalanceTint = 5f,
                clarityAmount = -0.2f,
                sharpenAmount = 0.06f,
                halationIntensity = 0.08f,
                halationThreshold = 0.88f,
                exposure = 0.05f,
                hslSaturationScale = floatArrayOf(1.0f, 1.0f, 1.0f, 0.92f, 1.0f, 1.0f, 1.0f, 1.0f),
                hslLuminanceScale = floatArrayOf(1.0f, 1.0f, 1.0f, 1.05f, 1.0f, 1.0f, 1.0f, 1.0f)
            )
        ),
        BuiltinPreset(
            id = "portrait_warm",
            nameZh = "暖色人像",
            nameEn = "Warm Portrait",
            category = PresetCategory.Portrait,
            params = PipelineParams(
                contrast = -0.08f,
                saturation = 0.1f,
                vibrance = 0.12f,
                shadows = 0.12f,
                highlights = -0.05f,
                whiteBalanceTemp = 7200f,
                whiteBalanceTint = 8f,
                exposure = 0.1f,
                clarityAmount = -0.1f,
                tintHighlightHue = 28f,
                tintHighlightStrength = 0.12f,
                tintShadowHue = 20f,
                tintShadowStrength = 0.06f,
                halationIntensity = 0.12f,
                halationRedBias = 0.7f
            )
        ),
        BuiltinPreset(
            id = "portrait_dramatic",
            nameZh = "戏剧人像",
            nameEn = "Dramatic Portrait",
            category = PresetCategory.Portrait,
            params = PipelineParams(
                contrast = 0.25f,
                saturation = -0.08f,
                vibrance = 0.05f,
                shadows = -0.15f,
                highlights = -0.2f,
                clarityAmount = 0.2f,
                sharpenAmount = 0.12f,
                whiteBalanceTemp = 6200f,
                lensVignetteStrength = 0.2f,
                sigmoidContrast = 0.12f,
                toneCurveY = floatArrayOf(0f, 0.18f, 0.45f, 0.72f, 0.95f)
            )
        ),
        BuiltinPreset(
            id = "portrait_natural_glow",
            nameZh = "自然光泽",
            nameEn = "Natural Glow",
            category = PresetCategory.Portrait,
            params = PipelineParams(
                contrast = -0.05f,
                saturation = 0.08f,
                vibrance = 0.1f,
                shadows = 0.08f,
                highlights = 0.05f,
                exposure = 0.08f,
                whiteBalanceTemp = 6600f,
                whiteBalanceTint = 3f,
                clarityAmount = -0.05f,
                halationIntensity = 0.1f,
                halationThreshold = 0.85f,
                tintHighlightHue = 35f,
                tintHighlightStrength = 0.06f
            )
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // 风景增强 / Landscape
    // ═══════════════════════════════════════════════════════════════

    private val landscapePresets = listOf(
        BuiltinPreset(
            id = "landscape_vivid",
            nameZh = "鲜艳风景",
            nameEn = "Vivid Landscape",
            category = PresetCategory.Landscape,
            params = PipelineParams(
                contrast = 0.2f,
                saturation = 0.3f,
                vibrance = 0.25f,
                highlights = -0.15f,
                shadows = 0.12f,
                clarityAmount = 0.35f,
                dehazeAmount = 0.15f,
                textureAmount = 0.2f,
                sharpenAmount = 0.15f,
                whiteBalanceTemp = 6300f,
                hslSaturationScale = floatArrayOf(1.0f, 1.1f, 1.1f, 1.3f, 1.0f, 1.0f, 1.0f, 1.0f),
                hslLuminanceScale = floatArrayOf(1.0f, 0.95f, 1.0f, 0.9f, 1.0f, 1.0f, 1.0f, 1.0f),
                exposure = 0.05f
            )
        ),
        BuiltinPreset(
            id = "landscape_golden_hour",
            nameZh = "黄金时刻",
            nameEn = "Golden Hour",
            category = PresetCategory.Landscape,
            params = PipelineParams(
                contrast = 0.1f,
                saturation = 0.18f,
                vibrance = 0.22f,
                highlights = -0.08f,
                shadows = 0.08f,
                whiteBalanceTemp = 7800f,
                whiteBalanceTint = 12f,
                exposure = 0.1f,
                tintHighlightHue = 32f,
                tintHighlightStrength = 0.18f,
                tintShadowHue = 25f,
                tintShadowStrength = 0.1f,
                halationIntensity = 0.1f,
                halationRedBias = 0.8f
            )
        ),
        BuiltinPreset(
            id = "landscape_moody_sky",
            nameZh = "阴郁天空",
            nameEn = "Moody Sky",
            category = PresetCategory.Landscape,
            params = PipelineParams(
                contrast = 0.18f,
                saturation = -0.12f,
                vibrance = -0.05f,
                highlights = -0.25f,
                shadows = -0.08f,
                exposure = -0.1f,
                whiteBalanceTemp = 5800f,
                whiteBalanceTint = -4f,
                clarityAmount = 0.15f,
                dehazeAmount = 0.2f,
                tintShadowHue = 210f,
                tintShadowStrength = 0.12f,
                colorWheelLiftB = 0.03f,
                lensVignetteStrength = 0.12f
            )
        ),
        BuiltinPreset(
            id = "landscape_forest_green",
            nameZh = "森林绿意",
            nameEn = "Forest Green",
            category = PresetCategory.Landscape,
            params = PipelineParams(
                contrast = 0.08f,
                saturation = 0.15f,
                vibrance = 0.18f,
                shadows = 0.1f,
                highlights = -0.05f,
                clarityAmount = 0.2f,
                textureAmount = 0.15f,
                dehazeAmount = 0.1f,
                whiteBalanceTemp = 6400f,
                hslHueShift = floatArrayOf(0f, 0f, -3f, 2f, 0f, 0f, 0f, 0f),
                hslSaturationScale = floatArrayOf(1.0f, 1.05f, 1.1f, 1.25f, 1.0f, 1.0f, 1.0f, 1.0f),
                hslLuminanceScale = floatArrayOf(1.0f, 1.0f, 1.05f, 0.95f, 1.0f, 1.0f, 1.0f, 1.0f)
            )
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // 街拍风格 / Street
    // ═══════════════════════════════════════════════════════════════

    private val streetPresets = listOf(
        BuiltinPreset(
            id = "street_urban_contrast",
            nameZh = "城市对比",
            nameEn = "Urban Contrast",
            category = PresetCategory.Street,
            params = PipelineParams(
                contrast = 0.3f,
                saturation = -0.05f,
                vibrance = 0.05f,
                highlights = -0.12f,
                shadows = -0.08f,
                clarityAmount = 0.25f,
                sharpenAmount = 0.2f,
                whiteBalanceTemp = 6200f,
                sigmoidContrast = 0.1f,
                toneCurveY = floatArrayOf(0f, 0.2f, 0.48f, 0.75f, 1f)
            )
        ),
        BuiltinPreset(
            id = "street_neon_night",
            nameZh = "霓虹夜色",
            nameEn = "Neon Night",
            category = PresetCategory.Street,
            params = PipelineParams(
                exposure = -0.15f,
                contrast = 0.22f,
                saturation = 0.2f,
                vibrance = 0.25f,
                shadows = -0.1f,
                highlights = -0.2f,
                whiteBalanceTemp = 5200f,
                whiteBalanceTint = -8f,
                clarityAmount = 0.15f,
                tintHighlightHue = 280f,
                tintHighlightStrength = 0.15f,
                tintShadowHue = 200f,
                tintShadowStrength = 0.18f,
                hslSaturationScale = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.2f, 1.3f, 1.0f, 1.0f)
            )
        ),
        BuiltinPreset(
            id = "street_desaturated",
            nameZh = "褪色街拍",
            nameEn = "Desaturated",
            category = PresetCategory.Street,
            params = PipelineParams(
                contrast = 0.12f,
                saturation = -0.35f,
                vibrance = -0.15f,
                shadows = 0.05f,
                highlights = -0.08f,
                clarityAmount = 0.18f,
                whiteBalanceTemp = 6400f,
                filmGrainIntensity = 0.08f,
                toneCurveY = floatArrayOf(0.03f, 0.22f, 0.48f, 0.73f, 0.97f)
            )
        ),
        BuiltinPreset(
            id = "street_hard_light",
            nameZh = "硬光",
            nameEn = "Hard Light",
            category = PresetCategory.Street,
            params = PipelineParams(
                contrast = 0.4f,
                saturation = -0.1f,
                highlights = -0.1f,
                shadows = -0.2f,
                clarityAmount = 0.3f,
                sharpenAmount = 0.25f,
                whiteBalanceTemp = 6000f,
                sigmoidContrast = 0.15f,
                lensVignetteStrength = -0.08f
            )
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // 复古怀旧 / Vintage
    // ═══════════════════════════════════════════════════════════════

    private val vintagePresets = listOf(
        BuiltinPreset(
            id = "vintage_70s_fade",
            nameZh = "70s 褪色",
            nameEn = "70s Fade",
            category = PresetCategory.Vintage,
            params = PipelineParams(
                contrast = -0.15f,
                saturation = -0.25f,
                vibrance = -0.1f,
                shadows = 0.2f,
                highlights = -0.05f,
                whiteBalanceTemp = 7200f,
                whiteBalanceTint = 8f,
                exposure = 0.08f,
                filmGrainIntensity = 0.15f,
                halationIntensity = 0.12f,
                halationRedBias = 0.6f,
                tintHighlightHue = 38f,
                tintHighlightStrength = 0.1f,
                toneCurveY = floatArrayOf(0.06f, 0.25f, 0.48f, 0.72f, 0.95f)
            )
        ),
        BuiltinPreset(
            id = "vintage_80s_pop",
            nameZh = "80s 流行",
            nameEn = "80s Pop",
            category = PresetCategory.Vintage,
            params = PipelineParams(
                contrast = 0.18f,
                saturation = 0.25f,
                vibrance = 0.2f,
                shadows = 0.05f,
                highlights = -0.05f,
                whiteBalanceTemp = 6800f,
                whiteBalanceTint = 6f,
                hslSaturationScale = floatArrayOf(1.1f, 1.15f, 1.1f, 1.2f, 1.0f, 1.15f, 1.0f, 1.0f),
                tintHighlightHue = 320f,
                tintHighlightStrength = 0.08f,
                tintShadowHue = 180f,
                tintShadowStrength = 0.06f
            )
        ),
        BuiltinPreset(
            id = "vintage_sepia_warm",
            nameZh = "棕褐暖调",
            nameEn = "Sepia Warm",
            category = PresetCategory.Vintage,
            params = PipelineParams(
                contrast = -0.05f,
                saturation = -0.4f,
                vibrance = -0.2f,
                shadows = 0.1f,
                highlights = -0.05f,
                whiteBalanceTemp = 7500f,
                whiteBalanceTint = 10f,
                exposure = 0.05f,
                tintHighlightHue = 35f,
                tintHighlightStrength = 0.2f,
                tintShadowHue = 30f,
                tintShadowStrength = 0.15f,
                colorWheelLiftR = 0.05f,
                colorWheelLiftB = -0.04f,
                colorWheelGammaR = 1.05f,
                colorWheelGammaB = 0.95f
            )
        ),
        BuiltinPreset(
            id = "vintage_cross_process",
            nameZh = "交叉冲洗",
            nameEn = "Cross Process",
            category = PresetCategory.Vintage,
            params = PipelineParams(
                contrast = 0.15f,
                saturation = 0.15f,
                vibrance = 0.1f,
                shadows = 0.05f,
                highlights = -0.1f,
                whiteBalanceTemp = 5800f,
                whiteBalanceTint = -12f,
                colorWheelLiftG = 0.04f,
                colorWheelLiftB = -0.03f,
                colorWheelGammaG = 1.08f,
                colorWheelGammaB = 0.92f,
                colorWheelGainG = 1.05f,
                colorWheelGainB = 0.95f,
                hslHueShift = floatArrayOf(0f, 5f, -5f, 3f, 0f, -3f, 5f, 0f),
                hslSaturationScale = floatArrayOf(1.0f, 1.1f, 1.15f, 0.9f, 1.0f, 1.1f, 1.0f, 1.0f)
            )
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // 黑白 / B&W
    // ═══════════════════════════════════════════════════════════════

    private val bnwPresets = listOf(
        BuiltinPreset(
            id = "bnw_classic",
            nameZh = "经典黑白",
            nameEn = "Classic B&W",
            category = PresetCategory.BnW,
            params = PipelineParams(
                contrast = 0.15f,
                saturation = -1f,
                vibrance = -1f,
                channelMixerMonochrome = true,
                channelMixerMatrix = floatArrayOf(
                    0.3f, 0.59f, 0.11f,
                    0.3f, 0.59f, 0.11f,
                    0.3f, 0.59f, 0.11f
                ),
                clarityAmount = 0.12f,
                sharpenAmount = 0.08f
            )
        ),
        BuiltinPreset(
            id = "bnw_high_contrast",
            nameZh = "高对比黑白",
            nameEn = "High Contrast B&W",
            category = PresetCategory.BnW,
            params = PipelineParams(
                contrast = 0.45f,
                saturation = -1f,
                vibrance = -1f,
                highlights = -0.15f,
                shadows = -0.2f,
                channelMixerMonochrome = true,
                channelMixerMatrix = floatArrayOf(
                    0.3f, 0.59f, 0.11f,
                    0.3f, 0.59f, 0.11f,
                    0.3f, 0.59f, 0.11f
                ),
                clarityAmount = 0.3f,
                sigmoidContrast = 0.2f,
                toneCurveY = floatArrayOf(0f, 0.15f, 0.45f, 0.78f, 1f)
            )
        ),
        BuiltinPreset(
            id = "bnw_film_noir",
            nameZh = "黑色电影",
            nameEn = "Film Noir",
            category = PresetCategory.BnW,
            params = PipelineParams(
                contrast = 0.5f,
                saturation = -1f,
                vibrance = -1f,
                exposure = -0.15f,
                shadows = -0.25f,
                highlights = -0.1f,
                channelMixerMonochrome = true,
                channelMixerMatrix = floatArrayOf(
                    0.25f, 0.6f, 0.15f,
                    0.25f, 0.6f, 0.15f,
                    0.25f, 0.6f, 0.15f
                ),
                clarityAmount = 0.25f,
                sharpenAmount = 0.15f,
                lensVignetteStrength = 0.3f,
                toneCurveY = floatArrayOf(0f, 0.1f, 0.35f, 0.7f, 0.95f)
            )
        ),
        BuiltinPreset(
            id = "bnw_infrared",
            nameZh = "红外",
            nameEn = "Infrared",
            category = PresetCategory.BnW,
            params = PipelineParams(
                contrast = 0.2f,
                saturation = -1f,
                vibrance = -1f,
                exposure = 0.15f,
                shadows = 0.1f,
                highlights = 0.05f,
                channelMixerMonochrome = true,
                channelMixerMatrix = floatArrayOf(
                    0.6f, 0.3f, 0.1f,
                    0.6f, 0.3f, 0.1f,
                    0.6f, 0.3f, 0.1f
                ),
                hslLuminanceScale = floatArrayOf(1.0f, 1.1f, 1.2f, 1.4f, 1.0f, 1.0f, 1.0f, 1.0f),
                halationIntensity = 0.15f,
                halationThreshold = 0.75f,
                toneCurveY = floatArrayOf(0.05f, 0.28f, 0.55f, 0.8f, 1f)
            )
        )
    )

    // ═══════════════════════════════════════════════════════════════
    // 创意风格 / Creative
    // ═══════════════════════════════════════════════════════════════

    private val creativePresets = listOf(
        BuiltinPreset(
            id = "creative_duotone_blue",
            nameZh = "双色调蓝",
            nameEn = "Duotone Blue",
            category = PresetCategory.Creative,
            params = PipelineParams(
                contrast = 0.1f,
                saturation = -0.6f,
                vibrance = -0.3f,
                shadows = 0.05f,
                highlights = -0.05f,
                tintHighlightHue = 200f,
                tintHighlightStrength = 0.35f,
                tintShadowHue = 220f,
                tintShadowStrength = 0.4f,
                colorWheelLiftB = 0.06f,
                colorWheelGammaB = 1.1f,
                colorWheelGainB = 1.08f
            )
        ),
        BuiltinPreset(
            id = "creative_teal_orange",
            nameZh = "电影青橙",
            nameEn = "Cinematic Teal-Orange",
            category = PresetCategory.Creative,
            params = PipelineParams(
                contrast = 0.18f,
                saturation = 0.1f,
                vibrance = 0.15f,
                highlights = -0.15f,
                shadows = 0.1f,
                whiteBalanceTemp = 6800f,
                whiteBalanceTint = 6f,
                tintHighlightHue = 30f,
                tintHighlightStrength = 0.22f,
                tintShadowHue = 195f,
                tintShadowStrength = 0.28f,
                tintBalance = 0.1f,
                clarityAmount = 0.15f,
                sigmoidContrast = 0.12f
            )
        ),
        BuiltinPreset(
            id = "creative_dreamy",
            nameZh = "梦幻",
            nameEn = "Dreamy",
            category = PresetCategory.Creative,
            params = PipelineParams(
                contrast = -0.18f,
                saturation = 0.1f,
                vibrance = 0.12f,
                shadows = 0.18f,
                highlights = 0.08f,
                exposure = 0.12f,
                clarityAmount = -0.25f,
                halationIntensity = 0.2f,
                halationThreshold = 0.78f,
                halationSpread = 15f,
                halationRedBias = 0.6f,
                tintHighlightHue = 280f,
                tintHighlightStrength = 0.08f,
                toneCurveY = floatArrayOf(0.04f, 0.25f, 0.52f, 0.78f, 0.98f)
            )
        ),
        BuiltinPreset(
            id = "creative_cyberpunk",
            nameZh = "赛博朋克",
            nameEn = "Cyberpunk",
            category = PresetCategory.Creative,
            params = PipelineParams(
                contrast = 0.25f,
                saturation = 0.15f,
                vibrance = 0.2f,
                shadows = -0.1f,
                highlights = -0.15f,
                exposure = -0.1f,
                whiteBalanceTemp = 5500f,
                whiteBalanceTint = -10f,
                tintHighlightHue = 290f,
                tintHighlightStrength = 0.25f,
                tintShadowHue = 180f,
                tintShadowStrength = 0.22f,
                clarityAmount = 0.2f,
                hslSaturationScale = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.3f, 1.35f, 1.0f, 1.0f),
                colorWheelLiftB = 0.04f,
                colorWheelGammaR = 1.05f,
                colorWheelGainB = 1.06f
            )
        )
    )
}
