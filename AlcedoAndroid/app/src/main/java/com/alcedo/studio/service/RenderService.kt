package com.alcedo.studio.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alcedo.studio.ndk.AlcedoNativeBridge
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
    @Volatile
    private var isShutdown = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val handlerLock = Any()

    private fun checkMemoryGuard(request: RenderRequest): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val availableMemory = maxMemory - usedMemory
        // Pixel copy requires width * height * channels * 4 bytes (Float), doubled for copy
        val requiredBytes = request.width.toLong() * request.height * request.channels * 4L * 2
        // Also account for the result array from native bridge
        val totalEstimate = requiredBytes + request.width.toLong() * request.height * request.channels * 4L
        if (totalEstimate > availableMemory) {
            Log.e("RenderService", "MemoryGuard: insufficient memory for request ${request.id}. " +
                    "Required ~${totalEstimate / (1024 * 1024)}MB, available ${availableMemory / (1024 * 1024)}MB")
            return false
        }
        return true
    }

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
                // MemoryGuard: check available memory before copying large pixel arrays
                if (!checkMemoryGuard(request)) {
                    Log.e("RenderService", "Rejecting request ${request.id} due to insufficient memory")
                    if (!cancelFlag.get() && request.callback != null) {
                        safePost {
                            request.callback?.invoke(false, request.pixels)
                        }
                    }
                    return@submit
                }

                // Copy pixel array to prevent concurrent modification
                val pixelsCopy = try {
                    request.pixels.copyOf()
                } catch (e: OutOfMemoryError) {
                    Log.e("RenderService", "OOM while copying pixels for request ${request.id}", e)
                    if (!cancelFlag.get() && request.callback != null) {
                        safePost {
                            request.callback?.invoke(false, request.pixels)
                        }
                    }
                    return@submit
                }

                // Build float pipeline parameters array for unified native bridge
                val paramsArray = buildRenderParamsArray(params)

                val resultPixels = AlcedoNativeBridge.applyPipelineFloat(
                    pixelsCopy, request.width, request.height, request.channels, paramsArray
                )
                val result = resultPixels != null

                // 修复: 回调应传递处理后的 resultPixels 而非原始 request.pixels
                val callbackPixels = if (result && resultPixels != null) resultPixels else request.pixels
                if (!cancelFlag.get() && request.callback != null) {
                    safePost {
                        request.callback?.invoke(result, callbackPixels)
                    }
                }
            } catch (e: Exception) {
                Log.e("RenderService", "Render failed for request ${request.id}", e)
                if (!cancelFlag.get() && request.callback != null) {
                    safePost {
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
        // 修复：清除主线程 Handler 中待执行的回调，防止 shutdown 后仍执行已销毁 UI 的回调
        synchronized(handlerLock) {
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    /**
     * 安全地向主线程 post 回调；如果 service 已 shutdown 则直接忽略。
     */
    private fun safePost(block: () -> Unit) {
        if (isShutdown) return
        synchronized(handlerLock) {
            if (!isShutdown) {
                mainHandler.post { if (!isShutdown) block() }
            }
        }
    }

    private fun buildRenderParamsArray(params: com.alcedo.studio.data.model.PipelineParams): FloatArray {
        val list = mutableListOf<Float>()
        list += params.exposure
        list += params.contrast
        list += params.saturation
        list += params.vibrance
        list += params.highlights
        list += params.shadows
        list += params.midtones
        list += params.whiteBalanceTemp
        list += params.whiteBalanceTint
        list += params.sharpenAmount
        list += params.clarityAmount
        list += params.clarityRadius
        list += params.filmGrainIntensity
        list += params.halationIntensity
        list += params.halationThreshold
        list += params.halationSpread
        list += params.halationRedBias
        list += params.sigmoidContrast
        list += params.colorWheelLiftR
        list += params.colorWheelLiftG
        list += params.colorWheelLiftB
        list += params.colorWheelGammaR
        list += params.colorWheelGammaG
        list += params.colorWheelGammaB
        list += params.colorWheelGainR
        list += params.colorWheelGainG
        list += params.colorWheelGainB
        list += params.tintHighlightHue
        list += params.tintHighlightStrength
        list += params.tintShadowHue
        list += params.tintShadowStrength
        list += params.tintBalance
        list += params.displayTransform.colorScience.ordinal.toFloat()
        list += params.displayTransform.eotf.ordinal.toFloat()
        list += params.displayTransform.peakLuminance
        list += params.displayTransform.displayColorSpace.ordinal.toFloat()
        list += params.shadowBoundary
        list += params.highlightBoundary
        return list.toFloatArray()
    }
}
