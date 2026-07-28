package com.alcedo.studio.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONNX Runtime model loading and inference manager. Owns the single
 * [OrtEnvironment] and a map of model-id -> [Session] handles for the CLIP,
 * SigLIP, mask-segmentation and captioner models. Provides typed helpers for
 * text/image encoding used by [ClipInferenceEngine].
 *
 * On Android the preferred execution provider is NNAPI (with a CPU fallback);
 * session options request a bounded thread pool to avoid contention.
 */
@Singleton
class OnnxModelManager @Inject constructor() {

    companion object {
        const val DEVICE_CPU = 0
        const val DEVICE_NNAPI = 1
        const val DEVICE_GPU = 2
        private const val TAG = "OnnxModelManager"
    }

    private data class Session(
        val modelId: String,
        val ort: OrtSession,
        val deviceId: Int,
        val inputNames: List<String>,
        val outputNames: List<String>,
    )

    private val env: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment().also {
            Log.i(TAG, "ONNX Runtime env ready")
        }
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val handles = ConcurrentHashMap<String, Long>()
    private var handleCounter = 1L

    /** Load an ONNX model file and return a non-zero handle on success. */
    fun loadModel(modelPath: String, deviceId: Int = DEVICE_NNAPI): Long {
        return runCatching {
            val opts = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(2)
                when (deviceId) {
                    DEVICE_NNAPI -> runCatching { addNnapi() }.onFailure {
                        Log.w(TAG, "NNAPI EP unavailable, falling back to CPU: ${it.message}")
                    }
                    DEVICE_GPU -> runCatching { addXnnpack(emptyMap()) }.onFailure {
                        Log.w(TAG, "XNNPACK EP unavailable: ${it.message}")
                    }
                }
            }
            val ort = env.createSession(modelPath, opts)
            val id = "model_${handleCounter++}"
            val session = Session(
                modelId = id,
                ort = ort,
                deviceId = deviceId,
                inputNames = ort.inputNames.toList(),
                outputNames = ort.outputNames.toList(),
            )
            sessions[id] = session
            val handle = handleCounter++
            handles[modelPath] = handle
            handleMap[handle] = id
            Log.i(TAG, "Loaded ONNX model $modelPath (id=$id, inputs=${session.inputNames})")
            handle
        }.onFailure {
            Log.e(TAG, "Failed to load ONNX model $modelPath", it)
        }.getOrDefault(0L)
    }

    private val handleMap = ConcurrentHashMap<Long, String>()

    /** Resolve the internal session id for an external handle. */
    fun handleFor(modelId: String): Long {
        // Caller passes the model-asset id; map it to the loaded session via the
        // last-loaded handle for that asset. We also accept the raw handle.
        return handles[modelId] ?: runCatching { modelId.toLong() }.getOrDefault(0L)
    }

    private fun sessionFor(handle: Long): Session? {
        val id = handleMap[handle] ?: return null
        return sessions[id]
    }

    /** Run the text encoder with a [tokens] array (shape [1, seqLen]). */
    fun runTextEncoder(handle: Long, tokens: IntArray): FloatArray {
        val session = sessionFor(handle) ?: return FloatArray(0)
        return runCatching {
            val seqLen = tokens.size
            val shape = longArrayOf(1L, seqLen.toLong())
            val tensor = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(tokens.size) { tokens[it].toLong() }), shape)
            val inputName = session.inputNames.first()
            val results = session.ort.run(mapOf(inputName to tensor))
            tensor.close()
            val output = results.first().value
            results.close()
            toFloatArray(output)
        }.onFailure { Log.w(TAG, "text encoder failed", it) }.getOrDefault(FloatArray(0))
    }

    /** Run the image encoder with NCHW RGB floats (shape [1,3,H,W]). */
    fun runImageEncoder(handle: Long, pixels: FloatArray, width: Int, height: Int): FloatArray {
        val session = sessionFor(handle) ?: return FloatArray(0)
        return runCatching {
            val shape = longArrayOf(1L, 3L, height.toLong(), width.toLong())
            val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), shape)
            val inputName = session.inputNames.first()
            val results = session.ort.run(mapOf(inputName to tensor))
            tensor.close()
            val output = results.first().value
            results.close()
            toFloatArray(output)
        }.onFailure { Log.w(TAG, "image encoder failed", it) }.getOrDefault(FloatArray(0))
    }

    /** Run a generic forward pass with named float inputs and return the first output. */
    fun runFloat(handle: Long, inputs: Map<String, FloatArray>, shapes: Map<String, LongArray>): FloatArray {
        val session = sessionFor(handle) ?: return FloatArray(0)
        return runCatching {
            val tensors = inputs.mapValues { (name, data) ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shapes[name] ?: longArrayOf(data.size.toLong()))
            }
            val results = session.ort.run(tensors)
            tensors.values.forEach { it.close() }
            val output = results.first().value
            results.close()
            toFloatArray(output)
        }.onFailure { Log.w(TAG, "float run failed", it) }.getOrDefault(FloatArray(0))
    }

    /** Unload a single model session by external handle. */
    fun unload(modelId: String) {
        val id = handleMap.remove(handles[modelId] ?: return) ?: return
        sessions.remove(id)?.ort?.close()
        handles.remove(modelId)
    }

    /** Release all sessions (low memory / app exit). */
    fun releaseAll() {
        sessions.values.forEach { runCatching { it.ort.close() } }
        sessions.clear()
        handles.clear()
        handleMap.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun toFloatArray(value: Any): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> {
            // ONNX can return [1][D] nested arrays.
            val first = value.firstOrNull() ?: return FloatArray(0)
            when (first) {
                is FloatArray -> first
                is Array<*> -> (first.firstOrNull() as? FloatArray) ?: FloatArray(0)
                else -> FloatArray(0)
            }
        }
        else -> FloatArray(0)
    }
}
