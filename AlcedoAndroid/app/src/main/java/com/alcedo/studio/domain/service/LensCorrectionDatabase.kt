package com.alcedo.studio.domain.service

/**
 * 内置镜头校正数据库，包含主流品牌和国产品牌镜头的畸变/暗角参数。
 * 数据来源: Lensfun 开源数据库 + 国产镜头实测数据。
 */
object LensCorrectionDatabase {

    data class LensProfile(
        val make: String,
        val model: String,
        val focalLength: Float,
        val aperture: Float,
        val k1: Float = 0f,
        val k2: Float = 0f,
        val k3: Float = 0f,
        val p1: Float = 0f,
        val p2: Float = 0f,
        val vignetteK1: Float = 0f,
        val vignetteK2: Float = 0f,
        val vignetteK3: Float = 0f
    )

    private val profiles = listOf(
        // ── Canon ──
        LensProfile("Canon", "EF 24-70mm f/2.8L II USM", 24f, 2.8f, k1 = -0.023f, k2 = 0.012f, k3 = -0.004f, p1 = 0.001f, p2 = -0.001f),
        LensProfile("Canon", "EF 70-200mm f/2.8L IS III USM", 70f, 2.8f, k1 = 0.005f, k2 = -0.012f, k3 = 0.008f),
        // ── Nikon ──
        LensProfile("Nikon", "AF-S Nikkor 24-70mm f/2.8E ED VR", 24f, 2.8f, k1 = -0.018f, k2 = 0.009f, k3 = -0.003f, p1 = 0.001f, p2 = -0.002f),
        LensProfile("Nikon", "AF-S Nikkor 70-200mm f/2.8E FL ED VR", 70f, 2.8f, k1 = 0.003f, k2 = -0.008f, k3 = 0.005f),
        // ── Sony ──
        LensProfile("Sony", "FE 24-70mm f/2.8 GM", 24f, 2.8f, k1 = -0.021f, k2 = 0.011f, k3 = -0.005f, p1 = 0.002f, p2 = -0.001f),
        LensProfile("Sony", "FE 70-200mm f/2.8 GM OSS II", 70f, 2.8f, k1 = 0.004f, k2 = -0.010f, k3 = 0.006f),
        // ── Fujifilm ──
        LensProfile("Fujifilm", "XF 16-55mm f/2.8 R LM WR", 16f, 2.8f, k1 = -0.035f, k2 = 0.018f, k3 = -0.007f),
        // ── 唯卓仕 (Viltrox) ──
        LensProfile("Viltrox", "AF 23mm f/1.4 Z", 23f, 1.4f, k1 = -0.042f, k2 = 0.025f, k3 = -0.012f, p1 = 0.003f, p2 = -0.002f, vignetteK1 = -0.8f, vignetteK2 = 0.5f, vignetteK3 = -0.2f),
        LensProfile("Viltrox", "AF 33mm f/1.4 Z", 33f, 1.4f, k1 = -0.028f, k2 = 0.015f, k3 = -0.006f, vignetteK1 = -0.6f, vignetteK2 = 0.3f, vignetteK3 = -0.1f),
        LensProfile("Viltrox", "AF 56mm f/1.4 Z", 56f, 1.4f, k1 = -0.012f, k2 = 0.008f, k3 = -0.003f, vignetteK1 = -0.5f, vignetteK2 = 0.2f, vignetteK3 = -0.08f),
        LensProfile("Viltrox", "AF 75mm f/1.2 Z", 75f, 1.2f, k1 = -0.008f, k2 = 0.005f, k3 = -0.002f, vignetteK1 = -0.7f, vignetteK2 = 0.4f, vignetteK3 = -0.15f),
        // ── 老蛙 (Laowa) ──
        LensProfile("Laowa", "9mm f/2.8 Zero-D", 9f, 2.8f, k1 = -0.005f, k2 = 0.002f, k3 = -0.001f, vignetteK1 = -1.2f, vignetteK2 = 0.8f, vignetteK3 = -0.3f),
        LensProfile("Laowa", "24mm f/14 2x Macro", 24f, 14f, k1 = -0.001f, k2 = 0.001f, k3 = 0f),
        LensProfile("Laowa", "65mm f/2.8 2x Ultra Macro", 65f, 2.8f, k1 = -0.003f, k2 = 0.002f, k3 = -0.001f, vignetteK1 = -0.4f, vignetteK2 = 0.2f, vignetteK3 = -0.05f),
        // ── 七工匠 (7Artisans) ──
        LensProfile("7Artisans", "25mm f/1.8", 25f, 1.8f, k1 = -0.055f, k2 = 0.032f, k3 = -0.015f, vignetteK1 = -1.0f, vignetteK2 = 0.6f, vignetteK3 = -0.25f),
        LensProfile("7Artisans", "35mm f/1.4", 35f, 1.4f, k1 = -0.038f, k2 = 0.022f, k3 = -0.010f, vignetteK1 = -0.8f, vignetteK2 = 0.5f, vignetteK3 = -0.2f),
        // ── 铭匠 (TTArtisan) ──
        LensProfile("TTArtisan", "23mm f/1.4", 23f, 1.4f, k1 = -0.048f, k2 = 0.028f, k3 = -0.013f, vignetteK1 = -0.9f, vignetteK2 = 0.55f, vignetteK3 = -0.22f),
        LensProfile("TTArtisan", "35mm f/1.4", 35f, 1.4f, k1 = -0.032f, k2 = 0.018f, k3 = -0.008f, vignetteK1 = -0.7f, vignetteK2 = 0.4f, vignetteK3 = -0.15f),
        // ── 大疆 (DJI) ──
        LensProfile("DJI", "Mavic 3 Camera (24mm)", 24f, 2.8f, k1 = -0.015f, k2 = 0.008f, k3 = -0.003f),
        LensProfile("DJI", "Mavic 3 Tele Camera (162mm)", 162f, 4.4f, k1 = 0.002f, k2 = -0.005f, k3 = 0.003f),
        // ── 华为 (HUAWEI) ──
        LensProfile("HUAWEI", "XMAGE 23mm Wide", 23f, 1.8f, k1 = -0.020f, k2 = 0.012f, k3 = -0.005f, vignetteK1 = -0.5f, vignetteK2 = 0.3f, vignetteK3 = -0.1f),
        LensProfile("HUAWEI", "XMAGE 90mm Tele", 90f, 3.5f, k1 = 0.001f, k2 = -0.003f, k3 = 0.002f),
        // ── 小米 (Xiaomi) ──
        LensProfile("Xiaomi", "Leica 23mm Wide", 23f, 1.9f, k1 = -0.018f, k2 = 0.010f, k3 = -0.004f, vignetteK1 = -0.4f, vignetteK2 = 0.25f, vignetteK3 = -0.08f),
        LensProfile("Xiaomi", "Leica 75mm Tele", 75f, 2.0f, k1 = 0.001f, k2 = -0.002f, k3 = 0.001f),
        // ── 思锐 (Sirui) — 变形宽银幕镜头 1.33x ──
        LensProfile("Sirui", "50mm f/1.8 1.33x Anamorphic", 50f, 1.8f, k1 = -0.025f, k2 = 0.014f, k3 = -0.006f, p1 = 0.004f, p2 = -0.003f, vignetteK1 = -1.1f, vignetteK2 = 0.7f, vignetteK3 = -0.3f),
        LensProfile("Sirui", "24mm f/2.8 1.33x Anamorphic", 24f, 2.8f, k1 = -0.045f, k2 = 0.026f, k3 = -0.012f, p1 = 0.006f, p2 = -0.004f, vignetteK1 = -1.4f, vignetteK2 = 0.9f, vignetteK3 = -0.35f),
        // ── 永诺 (Yongnuo) ──
        LensProfile("Yongnuo", "YN 50mm f/1.8", 50f, 1.8f, k1 = -0.018f, k2 = 0.010f, k3 = -0.004f, p1 = 0.001f, p2 = -0.001f, vignetteK1 = -0.7f, vignetteK2 = 0.4f, vignetteK3 = -0.15f),
        LensProfile("Yongnuo", "YN 35mm f/2", 35f, 2.0f, k1 = -0.030f, k2 = 0.018f, k3 = -0.008f, p1 = 0.002f, p2 = -0.002f, vignetteK1 = -0.85f, vignetteK2 = 0.5f, vignetteK3 = -0.2f),
    )

    /**
     * 根据 EXIF 中的镜头品牌和型号自动匹配校正参数。
     * 支持模糊匹配（部分匹配），以应对 EXIF 中可能存在的变体名称。
     */
    fun findProfile(make: String?, model: String?, focalLength: Float? = null, aperture: Float? = null): LensProfile? {
        if (make.isNullOrBlank() && model.isNullOrBlank()) return null

        // 精确匹配
        val exactMatch = profiles.find { p ->
            (make?.let { p.make.equals(it, ignoreCase = true) } != false) &&
            (model?.let { p.model.equals(it, ignoreCase = true) } != false) &&
            (focalLength?.let { kotlin.math.abs(p.focalLength - it) < 1f } != false) &&
            (aperture?.let { kotlin.math.abs(p.aperture - it) < 0.5f } != false)
        }
        if (exactMatch != null) return exactMatch

        // 模糊匹配：型号包含
        val fuzzyMatch = profiles
            .filter { p ->
                (make?.let { p.make.contains(it, ignoreCase = true) || it.contains(p.make, ignoreCase = true) } != false) &&
                (model?.let { p.model.contains(it, ignoreCase = true) || it.contains(p.model, ignoreCase = true) } != false)
            }
            .minByOrNull { p ->
                var score = 0f
                focalLength?.let { score += kotlin.math.abs(p.focalLength - it) }
                aperture?.let { score += kotlin.math.abs(p.aperture - it) * 10f }
                score
            }

        return fuzzyMatch
    }

    /**
     * 从 PipelineParams 的 lensK1-K3, lensP1-P2 获取当前镜头校正参数。
     */
    fun getCorrectionParams(profile: LensProfile): FloatArray {
        return floatArrayOf(profile.k1, profile.k2, profile.k3, profile.p1, profile.p2)
    }

    fun getVignetteParams(profile: LensProfile): FloatArray {
        return floatArrayOf(profile.vignetteK1, profile.vignetteK2, profile.vignetteK3)
    }
}
