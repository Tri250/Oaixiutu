package com.alcedo.studio.service

import com.alcedo.studio.ndk.AlcedoNdkBridge
import com.alcedo.studio.utils.ThreadPool
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

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
    private var isShutdown = false

    fun submit(request: RenderRequest, paramsBuilder: com.alcedo.studio.data.model.PipelineParams.() -> Unit = {}) {
        if (isShutdown) return

        val params = com.alcedo.studio.data.model.PipelineParams().apply(paramsBuilder)
        val executor = when (request.priority) {
            RenderPriority.HIGH -> ThreadPool.computeExecutor
            RenderPriority.LOW -> ThreadPool.ioExecutor
            RenderPriority.NORMAL -> ThreadPool.computeExecutor
        }

        val future = executor.submit {
            val result = AlcedoNdkBridge.processPipeline(
                request.pixels, request.width, request.height, request.channels,
                exposure = params.exposure,
                contrast = params.contrast,
                highlights = params.highlights,
                shadows = params.shadows,
                saturation = params.saturation,
                vibrance = params.vibrance,
                clarity = params.clarity,
                sharpen = params.sharpen
            )
            request.callback?.invoke(result, request.pixels)
            pendingRequests.remove(request.id)
        }
        pendingRequests[request.id] = future
    }

    fun cancel(requestId: Long) {
        pendingRequests[requestId]?.cancel(true)
        pendingRequests.remove(requestId)
    }

    fun cancelAll() {
        pendingRequests.values.forEach { it.cancel(true) }
        pendingRequests.clear()
    }

    fun shutdown() {
        isShutdown = true
        cancelAll()
    }
}
