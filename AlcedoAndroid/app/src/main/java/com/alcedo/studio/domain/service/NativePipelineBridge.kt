package com.alcedo.studio.domain.service

class NativePipelineBridge {
    external fun nativeApplyPipeline(
        input: IntArray,
        width: Int,
        height: Int,
        exposure: Float,
        contrast: Float,
        saturation: Float,
        highlights: Float,
        shadows: Float,
        temperature: Float,
        tint: Float,
        sharpen: Float
    ): IntArray

    external fun nativeDecodeRaw(
        rawPath: String,
        demosaic: Int,
        highlightReconstruction: Boolean
    ): IntArray

    companion object {
        init {
            System.loadLibrary("alcedo_core")
        }
    }
}
