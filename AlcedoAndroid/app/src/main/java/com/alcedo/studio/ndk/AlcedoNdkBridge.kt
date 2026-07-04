package com.alcedo.studio.ndk

import android.util.Log

object AlcedoNdkBridge {
    private const val TAG = "AlcedoNdkBridge"

    var isAvailable = false
        private set

    init {
        try {
            System.loadLibrary("alcedo")
            isAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            isAvailable = false
        }
    }

    // Pipeline processing
    external fun nativeProcessPipeline(
        pixels: FloatArray, width: Int, height: Int, channels: Int,
        exposure: Float, contrast: Float, highlights: Float, shadows: Float,
        midtones: Float, shadowBoundary: Float, highlightBoundary: Float,
        whiteBalanceTemp: Float, whiteBalanceTint: Float,
        saturation: Float, vibrance: Float, clarity: Float, clarityRadius: Float,
        sharpen: Float, filmGrain: Float, halationIntensity: Float,
        halationThreshold: Float, halationSpread: Float, halationRedBias: Float,
        sigmoidContrast: Float,
        toneCurvePoints: Int, toneCurveX: FloatArray, toneCurveY: FloatArray,
        tintHighlightHue: Float, tintHighlightStrength: Float,
        tintShadowHue: Float, tintShadowStrength: Float, tintBalance: Float,
        colorWheelLift: FloatArray, colorWheelGamma: FloatArray, colorWheelGain: FloatArray,
        hslHueRanges: FloatArray, hslHueWidth: FloatArray,
        hslHueShift: FloatArray, hslSaturationScale: FloatArray, hslLuminanceScale: FloatArray,
        channelMixerMatrix: FloatArray, channelMixerMonochrome: Boolean,
        lutEnabled: Boolean, lutPath: String,
        lensK1: Float, lensK2: Float, lensK3: Float,
        lensP1: Float, lensP2: Float, lensCx: Float, lensCy: Float, lensFocalRatio: Float,
        lensVignetteStrength: Float,
        displayTransformColorScience: Int, displayTransformEotf: Int,
        displayTransformPeakLuminance: Float, displayTransformDisplayColorSpace: Int,
        autoExposureEnabled: Boolean, autoExposureTargetPercentile: Float,
        autoExposureTargetLuminance: Float
    ): Boolean

    external fun nativeGetPipelineInfo(): String
    external fun nativeSetBackend(backend: Int)
    external fun nativeSetWorkingColorSpace(space: Int)
    external fun nativeEnableStage(stage: Int, enable: Boolean)

    // Color science
    external fun nativeConvertColorSpace(pixels: FloatArray, count: Int, channels: Int, srcSpace: Int, dstSpace: Int)
    external fun nativeAcesRrt(pixels: FloatArray, count: Int, channels: Int, stride: Int)
    external fun nativeOpendrtToneMap(pixels: FloatArray, count: Int, channels: Int, stride: Int)
    external fun nativeSrgbEotf(r: FloatArray, g: FloatArray, b: FloatArray, count: Int)
    external fun nativePqEotf(r: FloatArray, g: FloatArray, b: FloatArray, count: Int, peakLuminance: Float)
    external fun nativeHlgOetf(r: FloatArray, g: FloatArray, b: FloatArray, count: Int)
    external fun nativeSigmoidContrast(pixels: FloatArray, count: Int, channels: Int, amount: Float)

    // Auto exposure
    external fun nativeComputeAutoExposure(
        pixels: FloatArray, width: Int, height: Int, channels: Int,
        targetPercentile: Float, targetLuminance: Float
    ): Float

    fun processPipeline(
        pixels: FloatArray, width: Int, height: Int, channels: Int,
        exposure: Float = 0f, contrast: Float = 0f,
        highlights: Float = 0f, shadows: Float = 0f, midtones: Float = 0f,
        shadowBoundary: Float = 0.3f, highlightBoundary: Float = 0.7f,
        whiteBalanceTemp: Float = 6500f, whiteBalanceTint: Float = 0f,
        saturation: Float = 0f, vibrance: Float = 0f,
        clarity: Float = 0f, clarityRadius: Float = 10f,
        sharpen: Float = 0f, filmGrain: Float = 0f,
        halationIntensity: Float = 0f, halationThreshold: Float = 0.9f,
        halationSpread: Float = 1f, halationRedBias: Float = 0.3f,
        sigmoidContrast: Float = 0f,
        toneCurveX: FloatArray = FloatArray(0), toneCurveY: FloatArray = FloatArray(0),
        tintHighlightHue: Float = 0f, tintHighlightStrength: Float = 0f,
        tintShadowHue: Float = 0f, tintShadowStrength: Float = 0f, tintBalance: Float = 0.5f,
        colorWheelLift: FloatArray = floatArrayOf(0f, 0f, 0f),
        colorWheelGamma: FloatArray = floatArrayOf(1f, 1f, 1f),
        colorWheelGain: FloatArray = floatArrayOf(1f, 1f, 1f),
        hslHueRanges: FloatArray = FloatArray(8),
        hslHueWidth: FloatArray = FloatArray(8),
        hslHueShift: FloatArray = FloatArray(8),
        hslSaturationScale: FloatArray = FloatArray(8) { 1f },
        hslLuminanceScale: FloatArray = FloatArray(8) { 1f },
        channelMixerMatrix: FloatArray = floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f),
        channelMixerMonochrome: Boolean = false,
        lutEnabled: Boolean = false, lutPath: String = "",
        lensK1: Float = 0f, lensK2: Float = 0f, lensK3: Float = 0f,
        lensP1: Float = 0f, lensP2: Float = 0f,
        lensCx: Float = 0.5f, lensCy: Float = 0.5f, lensFocalRatio: Float = 1f,
        lensVignetteStrength: Float = 0f,
        displayTransformColorScience: Int = 0,
        displayTransformEotf: Int = 0,
        displayTransformPeakLuminance: Float = 203f,
        displayTransformDisplayColorSpace: Int = 0,
        autoExposureEnabled: Boolean = false,
        autoExposureTargetPercentile: Float = 0.5f,
        autoExposureTargetLuminance: Float = 0.18f
    ): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("processPipeline") {
            nativeProcessPipeline(
                pixels, width, height, channels,
                exposure, contrast, highlights, shadows, midtones,
                shadowBoundary, highlightBoundary,
                whiteBalanceTemp, whiteBalanceTint,
                saturation, vibrance, clarity, clarityRadius,
                sharpen, filmGrain, halationIntensity,
                halationThreshold, halationSpread, halationRedBias,
                sigmoidContrast,
                toneCurveX.size, toneCurveX, toneCurveY,
                tintHighlightHue, tintHighlightStrength,
                tintShadowHue, tintShadowStrength, tintBalance,
                colorWheelLift, colorWheelGamma, colorWheelGain,
                hslHueRanges, hslHueWidth,
                hslHueShift, hslSaturationScale, hslLuminanceScale,
                channelMixerMatrix, channelMixerMonochrome,
                lutEnabled, lutPath,
                lensK1, lensK2, lensK3,
                lensP1, lensP2, lensCx, lensCy, lensFocalRatio,
                lensVignetteStrength,
                displayTransformColorScience, displayTransformEotf,
                displayTransformPeakLuminance, displayTransformDisplayColorSpace,
                autoExposureEnabled, autoExposureTargetPercentile, autoExposureTargetLuminance
            )
        }
    }

    fun getPipelineInfo(): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("getPipelineInfo") {
            nativeGetPipelineInfo()
        }
    }

    fun setBackend(backend: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("setBackend") {
            nativeSetBackend(backend)
        }
    }

    fun setWorkingColorSpace(space: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("setWorkingColorSpace") {
            nativeSetWorkingColorSpace(space)
        }
    }

    fun enableStage(stage: Int, enable: Boolean) {
        if (!isAvailable) return
        NdkSafeCall.execute("enableStage") {
            nativeEnableStage(stage, enable)
        }
    }

    fun convertColorSpace(pixels: FloatArray, count: Int, channels: Int, srcSpace: Int, dstSpace: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("convertColorSpace") {
            nativeConvertColorSpace(pixels, count, channels, srcSpace, dstSpace)
        }
    }

    fun acesRrt(pixels: FloatArray, count: Int, channels: Int, stride: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("acesRrt") {
            nativeAcesRrt(pixels, count, channels, stride)
        }
    }

    fun opendrtToneMap(pixels: FloatArray, count: Int, channels: Int, stride: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("opendrtToneMap") {
            nativeOpendrtToneMap(pixels, count, channels, stride)
        }
    }

    fun srgbEotf(r: FloatArray, g: FloatArray, b: FloatArray, count: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("srgbEotf") {
            nativeSrgbEotf(r, g, b, count)
        }
    }

    fun pqEotf(r: FloatArray, g: FloatArray, b: FloatArray, count: Int, peakLuminance: Float) {
        if (!isAvailable) return
        NdkSafeCall.execute("pqEotf") {
            nativePqEotf(r, g, b, count, peakLuminance)
        }
    }

    fun hlgOetf(r: FloatArray, g: FloatArray, b: FloatArray, count: Int) {
        if (!isAvailable) return
        NdkSafeCall.execute("hlgOetf") {
            nativeHlgOetf(r, g, b, count)
        }
    }

    fun sigmoidContrast(pixels: FloatArray, count: Int, channels: Int, amount: Float) {
        if (!isAvailable) return
        NdkSafeCall.execute("sigmoidContrast") {
            nativeSigmoidContrast(pixels, count, channels, amount)
        }
    }

    fun computeAutoExposure(
        pixels: FloatArray, width: Int, height: Int, channels: Int,
        targetPercentile: Float, targetLuminance: Float
    ): Float {
        if (!isAvailable) return 0f
        return NdkSafeCall.executeFloat("computeAutoExposure") {
            nativeComputeAutoExposure(pixels, width, height, channels, targetPercentile, targetLuminance)
        }
    }
}
