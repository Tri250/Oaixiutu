package com.alcedo.studio.domain.service

import android.content.Context
import android.util.Log
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.gpu.GpuPipelineRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GPU 加速管线服务，作为 [PipelineService] 的高性能替代路径。
 *
 * 自动检测设备是否支持 OpenGL ES 3.1 Compute Shader，不支持时回退到 CPU 管线
 * （由调用方负责回退，本服务仅返回 null）。
 *
 * 注意：GPU 渲染器必须在拥有 GLES 3.1 上下文的线程上初始化与调用。
 * 本服务暴露 [initialize] / [processOnGpu] / [release] 三个核心方法：
 *   - [initialize]      应在 GL 线程上调用（如 GLSurfaceView.Renderer 回调）
 *   - [processOnGpu]    是 suspend 函数，内部使用 Dispatchers.Default，
 *                       但真正的 GL 调用仍需在 GL 线程执行；若调用方未在 GL
 *                       线程，应自行 post 到 GL 线程。本实现保持轻量，由
 *                       [PipelineService] 协调线程。
 *   - [release]         释放 GPU 资源。
 */
class GpuPipelineService(private val context: Context) {

    companion object {
        private const val TAG = "GpuPipelineService"
    }

    private var renderer: GpuPipelineRenderer? = null
    private var isGpuAvailable = false
    private var initializedWidth = 0
    private var initializedHeight = 0

    /**
     * 检测设备是否支持 GPU Compute Shader。
     *
     * 结果会被缓存到 [isGpuAvailable]，后续调用 [initialize] / [processOnGpu]
     * 会先检查此标志。
     *
     * @return true 表示支持 GLES 3.1+。
     */
    fun checkGpuSupport(): Boolean {
        if (!isGpuAvailable) {
            isGpuAvailable = GpuPipelineRenderer.checkComputeSupport(context)
            Log.i(TAG, "GPU Compute support: $isGpuAvailable")
        }
        return isGpuAvailable
    }

    /**
     * 初始化 GPU 渲染器。必须在 GL 线程上调用。
     *
     * 若尺寸发生变化，会自动释放并重建渲染器。
     *
     * @param width  图像宽度
     * @param height 图像高度
     */
    fun initialize(width: Int, height: Int) {
        if (!isGpuAvailable) return

        if (renderer == null) {
            renderer = GpuPipelineRenderer()
        }

        if (initializedWidth != width || initializedHeight != height) {
            renderer?.release()
            renderer = GpuPipelineRenderer()
            renderer?.initialize(width, height)
            initializedWidth = width
            initializedHeight = height
        }
    }

    /**
     * 使用 GPU 执行核心管线处理（曝光 / 对比度 / 白平衡 / 饱和度 / 高光 / 阴影等）。
     *
     * 本方法不会执行 GPU 不擅长的算子（HSL、色调曲线、镜头校正、LUT 等），
     * 这些算子应由调用方在 GPU pass 之后通过 CPU 管线补充。
     *
     * @param input  输入 RGBA float 数组，长度 width*height*4，每通道 0..1。
     * @param width  图像宽度
     * @param height 图像高度
     * @param params 管线参数
     * @return 处理后的 RGBA float 数组；若 GPU 不可用或处理失败，返回 null，
     *         调用方应回退到 CPU 管线。
     */
    suspend fun processOnGpu(
        input: FloatArray,
        width: Int,
        height: Int,
        params: PipelineParams
    ): FloatArray? = withContext(Dispatchers.Default) {
        if (!isGpuAvailable || renderer == null) return@withContext null

        try {
            renderer?.uploadInputImage(input, width, height)

            // 构建 uniform 参数数组，顺序与 GpuPipelineRenderer.executePipeline 一致。
            // 注意：whiteBalanceTemp 在 PipelineParams 中是绝对色温值（如 6500），
            // GPU shader 期望的是相对偏移量，这里做一次粗略的中心化（以 6500K 为中性）。
            val tempOffset = (params.whiteBalanceTemp - 6500f).coerceIn(-2000f, 5000f)
            val uniformParams = floatArrayOf(
                params.exposure,
                params.contrast,
                tempOffset,
                params.whiteBalanceTint,
                params.highlights,
                params.shadows,
                0f,                                // whites（PipelineParams 无独立字段，保持 0）
                0f,                                // blacks（同上）
                params.saturation,
                params.vibrance,
                params.clarityAmount,
                0f,                                // dehaze（PipelineParams 无独立字段）
                width.toFloat(),
                height.toFloat()
            )

            renderer?.executePipeline(uniformParams)
        } catch (e: Exception) {
            Log.e(TAG, "GPU pipeline failed, falling back to CPU", e)
            null
        }
    }

    /**
     * 在 GPU 管线输出之上执行锐化 pass。
     *
     * 必须在 [processOnGpu] 成功之后调用。
     *
     * @param amount 锐化强度（0..1+）
     * @param radius 卷积半径（像素）
     * @return 锐化后的 RGBA float 数组；失败返回 null。
     */
    suspend fun sharpenOnGpu(amount: Float, radius: Float): FloatArray? =
        withContext(Dispatchers.Default) {
            if (!isGpuAvailable || renderer == null) return@withContext null
            try {
                renderer?.executeSharpen(amount, radius)
            } catch (e: Exception) {
                Log.e(TAG, "GPU sharpen failed", e)
                null
            }
        }

    /**
     * 释放 GPU 资源。应在 GL 线程调用。
     */
    fun release() {
        renderer?.release()
        renderer = null
        initializedWidth = 0
        initializedHeight = 0
    }
}
