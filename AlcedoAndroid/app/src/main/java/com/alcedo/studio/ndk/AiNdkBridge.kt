package com.alcedo.studio.ndk

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext

/**
 * AI-specific JNI bridge. Wraps the ONNX Runtime + native CLIP/segmentation
 * entry points declared on [AlcedoNativeBridge] into a typed, suspendable API
 * used by [com.alcedo.studio.ai.OnnxModelManager] and the CLIP inference engine.
 *
 * Heavy inference is dispatched onto [ThreadPool.aiInference] to avoid blocking
 * the pipeline compute pool.
 */
@Keep
object AiNdkBridge {

    /** Device ids passed to the native ONNX Runtime session options. */
    const val DEVICE_CPU = 0
    const val DEVICE_NNAPI = 1
    const val DEVICE_GPU = 2

    data class ModelHandle(val value: Long) {
        val isValid: Boolean get() = value != 0L
    }

    /** Load an ONNX model and return a session handle. */
    suspend fun loadModel(modelPath: String, deviceId: Int = DEVICE_NNAPI): ModelHandle =
        withContext(ThreadPool.aiInference) {
            val h = NdkSafeCall.handle {
                AlcedoNativeBridge.nativeAiLoadOnnxModel(modelPath, deviceId)
            }
            ModelHandle(h)
        }

    /** Run the CLIP/SigLIP text encoder. Returns an L2-normalised embedding. */
    suspend fun encodeText(handle: ModelHandle, text: String): FloatArray =
        withContext(ThreadPool.aiInference) {
            if (!handle.isValid) return@withContext FloatArray(0)
            NdkSafeCall.call(default = FloatArray(0)) {
                AlcedoNativeBridge.nativeAiRunClipText(handle.value, text)
            }
        }

    /** Run the CLIP/SigLIP image encoder over a decoded native image handle. */
    suspend fun encodeImage(handle: ModelHandle, imageHandle: Long): FloatArray =
        withContext(ThreadPool.aiInference) {
            if (!handle.isValid || imageHandle == 0L) return@withContext FloatArray(0)
            NdkSafeCall.call(default = FloatArray(0)) {
                AlcedoNativeBridge.nativeAiRunClipImage(handle.value, imageHandle)
            }
        }

    /** Run a segmentation model and return a coverage bitmap (mask). */
    suspend fun runSegmentation(handle: ModelHandle, imageHandle: Long): Bitmap? =
        withContext(ThreadPool.aiInference) {
            if (!handle.isValid || imageHandle == 0L) return@withContext null
            NdkSafeCall.callOrNull {
                AlcedoNativeBridge.nativeAiRunSegmentation(handle.value, imageHandle)
            }
        }

    /** Release an ONNX session handle. */
    fun releaseModel(handle: ModelHandle) {
        if (!handle.isValid) return
        NdkSafeCall.run { AlcedoNativeBridge.nativeAiReleaseModel(handle.value) }
    }

    /**
     * Batch-encode a list of images concurrently (bounded by the AI pool).
     * Returns embeddings keyed by image handle. Failed encodings yield empty
     * arrays and are filtered out by the caller.
     */
    suspend fun encodeImagesBatch(
        handle: ModelHandle,
        imageHandles: List<Long>,
    ): Map<Long, FloatArray> = withContext(ThreadPool.aiInference) {
        if (!handle.isValid) return@withContext emptyMap()
        imageHandles.mapNotNull { ih ->
            if (ih == 0L) return@mapNotNull null
            val emb = NdkSafeCall.call(default = FloatArray(0)) {
                AlcedoNativeBridge.nativeAiRunClipImage(handle.value, ih)
            }
            if (emb.isNotEmpty()) ih to emb else null
        }.toMap()
    }
}
