package com.alcedo.studio.gpu

import android.graphics.Bitmap
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall

/**
 * Vulkan pipeline renderer for Compose. Bridges a native pipeline handle to a
 * SurfaceView so the editor viewport can display the GPU-rendered final display
 * frame. The native layer (vulkan/pipeline/vulkan_pipeline.cpp) owns the
 * swapchain; this class just forwards surface lifecycle + param invalidation.
 */
class GpuPipelineRenderer {

    @Volatile
    var pipelineHandle: Long = 0L
        private set

    @Volatile
    var surface: Surface? = null
        private set

    /** Attach a native pipeline handle to render. */
    fun bindPipeline(handle: Long) {
        pipelineHandle = handle
        invalidate()
    }

    /** Attach a surface from the SurfaceView holder. */
    fun attachSurface(newSurface: Surface?) {
        surface = newSurface
        if (newSurface != null && pipelineHandle != 0L) {
            NdkSafeCall.run { AlcedoNativeBridge.nativeInvalidateStage(pipelineHandle, "DisplayTransform") }
        }
    }

    /** Detach the current surface (on surface destroyed). */
    fun detachSurface() {
        surface = null
    }

    /**
     * Mark the display stage dirty so the native layer re-renders the next
     * frame. Despite the legacy name this is an invalidation request, not a
     * frame capture — use [captureFrame] to retrieve a bitmap.
     */
    fun invalidate() {
        if (pipelineHandle == 0L) return
        NdkSafeCall.run { AlcedoNativeBridge.nativeInvalidateStage(pipelineHandle, "DisplayTransform") }
    }

    /** Push updated [params] through the pipeline. */
    fun applyParams(params: AdjustmentParams) {
        if (pipelineHandle == 0L) return
        NdkSafeCall.call(default = false) {
            AlcedoNativeBridge.nativeApplyAdjustments(pipelineHandle, AlcedoNativeBridge.paramsToJson(params))
        }
    }

    /** Capture the current rendered frame as a bitmap. */
    fun captureFrame(maxWidth: Int = 2048): Bitmap? {
        if (pipelineHandle == 0L) return null
        return NdkSafeCall.callOrNull { AlcedoNativeBridge.nativeGetFinalDisplayFrame(pipelineHandle) }
    }

    /** Release resources (does not destroy the pipeline handle; owner does). */
    fun release() {
        detachSurface()
        pipelineHandle = 0L
    }

    companion object {
        private const val TAG = "GpuPipelineRenderer"
    }
}

/**
 * Compose entry point: renders the pipeline attached to [renderer] into a
 * SurfaceView. Recomposes when [params] changes, pushing the update to native.
 */
@Composable
fun GpuPipelineRendererView(
    renderer: GpuPipelineRenderer,
    params: AdjustmentParams,
    modifier: Modifier = Modifier,
) {
    var surfaceAvailable by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            android.view.SurfaceView(context).apply {
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        renderer.attachSurface(holder.surface)
                        surfaceAvailable = true
                    }

                    override fun surfaceChanged(
                        holder: android.view.SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        renderer.invalidate()
                    }

                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        renderer.detachSurface()
                        surfaceAvailable = false
                    }
                })
            }
        },
    )

    LaunchedEffect(params, surfaceAvailable) {
        if (surfaceAvailable) renderer.applyParams(params)
    }

    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }
}
