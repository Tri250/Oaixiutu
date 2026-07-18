package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.*
import com.alcedo.studio.ndk.AlcedoNativeBridge

// TODO(dead-code): 色彩科学桥接未接入 UI。当前 GPU 渲染管线 (GpuPipelineService) 处理色彩变换，未使用此 JNI 桥接。
//   待离线色彩空间转换或 ACES/OpenDRT 工作流集成时启用。

/**
 * JNI bridge for color science operations.
 * Provides access to native ACES 2.0, OpenDRT, and color space transforms.
 */
class ColorScienceBridge {
    private val nativeBridge = AlcedoNativeBridge

    /**
     * Apply ACES 2.0 output rendering transform to float RGB data.
     * Input: linear sRGB, Output: sRGB with EOTF applied.
     */
    fun applyAcesTransform(pixels: FloatArray, width: Int, height: Int, peakLuminance: Float = 100f): FloatArray {
        return nativeBridge.nativeApplyAcesTransform(pixels, width, height, peakLuminance)
    }

    /**
     * Apply OpenDRT display rendering transform to float RGB data.
     */
    fun applyOpenDRTTransform(pixels: FloatArray, width: Int, height: Int, peakLuminance: Float = 100f): FloatArray {
        return nativeBridge.nativeApplyOpenDRTTransform(pixels, width, height, peakLuminance)
    }

    /**
     * Convert between color spaces.
     * Space codes: 0=sRGB, 1=Display P3, 2=Rec2020, 3=ACES AP0, 4=ACES AP1
     */
    fun convertColorSpace(pixels: FloatArray, width: Int, height: Int, srcSpace: Int, dstSpace: Int): FloatArray {
        return nativeBridge.nativeConvertColorSpace(pixels, width, height, srcSpace, dstSpace)
    }

    /**
     * Apply EOTF to linear RGB data.
     * eotfType: 0=sRGB, 1=PQ, 2=HLG, 3=Gamma2.2, 4=Gamma2.4
     */
    fun applyEOTF(pixels: FloatArray, width: Int, height: Int, eotfType: Int, peakLuminance: Float = 100f): FloatArray {
        return nativeBridge.nativeApplyEOTF(pixels, width, height, eotfType, peakLuminance)
    }

    /**
     * Apply sigmoid contrast curve.
     */
    fun applySigmoidContrast(pixels: FloatArray, width: Int, height: Int, contrast: Float, pivot: Float = 0.18f, shoulder: Float = 0.5f): FloatArray {
        return nativeBridge.nativeApplySigmoidContrast(pixels, width, height, contrast, pivot, shoulder)
    }

    /**
     * Get display transform for a given ColorScience mode.
     */
    fun getDisplayTransform(mode: ColorScience, peakLuminance: Float = 100f): (FloatArray, Int, Int) -> FloatArray {
        return when (mode) {
            ColorScience.ACES20 -> { pixels, w, h -> applyAcesTransform(pixels, w, h, peakLuminance) }
            ColorScience.OPENDRT -> { pixels, w, h -> applyOpenDRTTransform(pixels, w, h, peakLuminance) }
            ColorScience.LINEAR -> { pixels, w, h -> applyEOTF(pixels, w, h, 0, peakLuminance) }
        }
    }

    // ============================================================
    // OKLab conversions (convenience)
    // ============================================================

    fun srgbToOklab(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val result = nativeBridge.nativeSrgbToOklab(r, g, b)
        return Triple(result[0], result[1], result[2])
    }

    fun oklabToSrgb(L: Float, a: Float, bb: Float): Triple<Float, Float, Float> {
        val result = nativeBridge.nativeOklabToSrgb(L, a, bb)
        return Triple(result[0], result[1], result[2])
    }
}