package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Higher-level GPU pipeline management. Coordinates stage invalidation and
 * accelerator selection across the fused edit pipeline
 * (core/edit/pipeline/pipeline_vulkan_impl.cpp). The actual per-image pipeline
 * state lives in [PipelineService]; this service provides device-wide policy.
 */
@Singleton
class GpuPipelineService @Inject constructor(
    private val gpuService: GpuService,
) {

    /** Stage names matching the desktop pipeline graph. */
    val stages: List<String> = listOf(
        "Basic", "Color", "CST", "Detail", "Curve", "Wheel", "Geometry", "Raw", "FilmGrain", "Halation",
    )

    /** True when the device can run the fused Vulkan pipeline. */
    fun isAccelerated(): Boolean = gpuService.isAvailable()

    /** Invalidate a named stage so the next render recomputes it. */
    fun invalidateStage(pipelineHandle: Long, stageName: String) {
        if (pipelineHandle == 0L) return
        NdkSafeCall.run { AlcedoNativeBridge.nativeInvalidateStage(pipelineHandle, stageName) }
    }

    /** Invalidate all stages (full re-render). */
    fun invalidateAll(pipelineHandle: Long) {
        stages.forEach { invalidateStage(pipelineHandle, it) }
    }

    /** Select the accelerator for the next pipeline build. */
    fun selectAccelerator(): Accelerator = if (gpuService.isAvailable()) Accelerator.VULKAN else Accelerator.CPU

    enum class Accelerator { CPU, VULKAN }

    /** Pretty device capability summary for the settings/about screen. */
    fun capabilitySummary(): String = buildString {
        append("GPU: ${gpuService.deviceName() ?: "unknown"}")
        append("\nAccelerator: ${selectAccelerator()}")
        append("\nVulkan compute: ${if (gpuService.isAvailable()) "available" else "unavailable"}")
        append("\nNative: ${gpuService.nativeVersion()}")
        append("\nStages: ${stages.size}")
    }

    companion object {
        private const val TAG = "GpuPipelineService"
    }
}
