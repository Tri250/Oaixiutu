package com.alcedo.studio.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alcedo.studio.ndk.AlcedoNdkBridge
import com.alcedo.studio.utils.ThreadPool
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

enum class RenderPriority { LOW, NORMAL, HIGH }

data class RenderRequest(
    val id: Long,
    val pixels: FloatArray,
    val width: Int,
    val height: Int,
    val channels: Int = 3,
    val priority: RenderPriority = RenderPriority.NORMAL,
    val callback: ((Boolean, FloatArray) -> Unit)? = null
)

class RenderService {
    private val pendingRequests = ConcurrentHashMap<Long, Future<*>>()
    private val cancelFlags = ConcurrentHashMap<Long, AtomicBoolean>()
    private var isShutdown = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun submit(request: RenderRequest, paramsBuilder: com.alcedo.studio.data.model.PipelineParams.() -> Unit = {}) {
        if (isShutdown) return

        val cancelFlag = AtomicBoolean(false)
        cancelFlags[request.id] = cancelFlag

        val params = com.alcedo.studio.data.model.PipelineParams().apply(paramsBuilder)
        val executor = when (request.priority) {
            RenderPriority.HIGH -> ThreadPool.computeExecutor
            RenderPriority.LOW -> ThreadPool.ioExecutor
            RenderPriority.NORMAL -> ThreadPool.computeExecutor
        }

        val future = executor.submit {
            if (cancelFlag.get()) {
                pendingRequests.remove(request.id)
                cancelFlags.remove(request.id)
                return@submit
            }

            try {
                // Copy pixel array to prevent concurrent modification
                val pixelsCopy = request.pixels.copyOf()

                val result = AlcedoNdkBridge.processPipeline(
                    pixelsCopy, request.width, request.height, request.channels,
                    exposure = params.exposure,
                    contrast = params.contrast,
                    highlights = params.highlights,
                    shadows = params.shadows,
                    saturation = params.saturation,
                    vibrance = params.vibrance,
                    clarity = params.clarity,
                    sharpen = params.sharpen
                )

                if (!cancelFlag.get() && request.callback != null) {
                    mainHandler.post {
                        request.callback?.invoke(result, request.pixels)
                    }
                }
            } catch (e: Exception) {
                Log.e("RenderService", "Render failed for request ${request.id}", e)
                if (!cancelFlag.get() && request.callback != null) {
                    mainHandler.post {
                        request.callback?.invoke(false, request.pixels)
                    }
                }
            } finally {
                pendingRequests.remove(request.id)
                cancelFlags.remove(request.id)
            }
        }
        pendingRequests[request.id] = future
    }

    fun cancel(requestId: Long) {
        cancelFlags[requestId]?.set(true)
        pendingRequests[requestId]?.cancel(true)
        pendingRequests.remove(requestId)
        cancelFlags.remove(requestId)
    }

    fun cancelAll() {
        cancelFlags.values.forEach { it.set(true) }
        pendingRequests.values.forEach { it.cancel(true) }
        pendingRequests.clear()
        cancelFlags.clear()
    }

    fun shutdown() {
        isShutdown = true
        cancelAll()
    }
}
