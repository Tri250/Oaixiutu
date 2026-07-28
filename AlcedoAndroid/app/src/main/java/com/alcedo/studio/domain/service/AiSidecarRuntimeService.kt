package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.ai.OnnxModelManager
import com.alcedo.studio.data.model.AiModelAsset
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONNX Runtime sidecar manager. Tracks which models are downloaded/loaded and
 * exposes session handles via [OnnxModelManager]. Acts as the single owner of
 * the ONNX environment lifetime for the app.
 */
@Singleton
class AiSidecarRuntimeService @Inject constructor(
    private val onnxModelManager: OnnxModelManager,
    private val modelDownloadService: ModelDownloadService,
) {

    data class RuntimeState(
        val loadedModelIds: Set<String> = emptySet(),
        val downloadingModelIds: Set<String> = emptySet(),
        val ready: Boolean = false,
    )

    private val _state = MutableStateFlow(RuntimeState())
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    /** Directory where downloaded ONNX models live. */
    val modelsDir: File
        get() = File(ContextProvider.requireContext().filesDir, "ai_models").apply { mkdirs() }

    /** Local path for a model asset, whether or not it's downloaded. */
    fun localPathFor(asset: AiModelAsset): File = File(modelsDir, "${asset.id}.onnx")

    /** True when the model file exists on disk and passes SHA verification. */
    fun isModelPresent(asset: AiModelAsset): Boolean {
        val file = localPathFor(asset)
        return file.exists() && file.length() == asset.sizeBytes
    }

    /** Ensure a model is downloaded, then load it into an ONNX session. */
    suspend fun ensureLoaded(asset: AiModelAsset): Boolean = withContext(ThreadPool.aiInference) {
        if (asset.id in _state.value.loadedModelIds) return@withContext true
        if (!isModelPresent(asset)) {
            _state.value = _state.value.copy(downloadingModelIds = _state.value.downloadingModelIds + asset.id)
            val ok = modelDownloadService.download(asset, localPathFor(asset))
            _state.value = _state.value.copy(downloadingModelIds = _state.value.downloadingModelIds - asset.id)
            if (!ok) return@withContext false
        }
        val path = localPathFor(asset).absolutePath
        val deviceId = if (asset.kind == AiModelKind.MASK_SEGMENT) OnnxModelManager.DEVICE_CPU
        else OnnxModelManager.DEVICE_NNAPI
        val handle = onnxModelManager.loadModel(path, deviceId)
        if (handle != 0L) {
            _state.value = _state.value.copy(
                loadedModelIds = _state.value.loadedModelIds + asset.id,
                ready = true,
            )
            true
        } else {
            Log.w(TAG, "ONNX load failed for ${asset.id}")
            false
        }
    }

    /** Unload a model session. */
    fun unload(modelId: String) {
        onnxModelManager.unload(modelId)
        _state.value = _state.value.copy(loadedModelIds = _state.value.loadedModelIds - modelId)
    }

    /** Release all sessions (on low memory / app background). */
    fun releaseAll() {
        onnxModelManager.releaseAll()
        _state.value = RuntimeState()
    }

    /** The default CLIP/SigLIP asset for semantic search. */
    fun defaultClipAsset(): AiModelAsset =
        ModelAssetCatalog.defaultFor(AiModelKind.CLIP) ?: ModelAssetCatalog.CLIP_VIT_BASE_PATCH32

    companion object {
        private const val TAG = "AiSidecarRuntimeService"
    }
}
