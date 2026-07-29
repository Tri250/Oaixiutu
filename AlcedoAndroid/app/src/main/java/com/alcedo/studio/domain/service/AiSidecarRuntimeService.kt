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
import java.security.MessageDigest
import java.util.Properties
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

    /**
     * SHA-256 integrity store. Persisted as a simple properties file so that
     * hashes computed on first download survive process restarts. When the
     * catalogue's [AiModelAsset.sha256] is empty, the hash is computed from the
     * downloaded file and stored here for subsequent verification.
     */
    private val shaStore: Properties by lazy {
        val file = File(modelsDir, "sha256.properties")
        val props = Properties()
        if (file.exists()) {
            runCatching { file.inputStream().use { props.load(it) } }
        }
        props
    }

    private fun saveShaStore() {
        val file = File(modelsDir, "sha256.properties")
        runCatching { file.outputStream().use { shaStore.store(it, "ONNX model SHA-256 hashes") } }
    }

    /** Local path for a model asset, whether or not it's downloaded. */
    fun localPathFor(asset: AiModelAsset): File = File(modelsDir, "${asset.id}.onnx")

    /** True when the model file exists on disk and passes SHA verification. */
    fun isModelPresent(asset: AiModelAsset): Boolean {
        val file = localPathFor(asset)
        if (!file.exists() || file.length() == 0L) return false
        // Verify against the catalogue SHA-256 if provided, otherwise check the
        // persisted hash (computed on first download). Skip when neither exists.
        val expected = asset.sha256.takeIf { it.isNotEmpty() }
            ?: shaStore.getProperty(asset.id)?.takeIf { it.isNotEmpty() }
        if (expected != null) {
            val actualSha = sha256(file)
            if (!actualSha.equals(expected, ignoreCase = true)) {
                Log.w(TAG, "SHA mismatch for ${asset.id}: $actualSha != $expected")
                return false
            }
        }
        return true
    }

    private fun sha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) md.update(buffer, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Ensure a model is downloaded, then load it into an ONNX session. */
    suspend fun ensureLoaded(asset: AiModelAsset): Boolean = withContext(ThreadPool.aiInference) {
        if (asset.id in _state.value.loadedModelIds) return@withContext true
        if (!isModelPresent(asset)) {
            _state.value = _state.value.copy(downloadingModelIds = _state.value.downloadingModelIds + asset.id)
            val ok = modelDownloadService.download(asset, localPathFor(asset))
            _state.value = _state.value.copy(downloadingModelIds = _state.value.downloadingModelIds - asset.id)
            if (!ok) return@withContext false
            // Compute and persist SHA-256 on first download when the catalogue
            // entry lacks a pre-known hash, so subsequent loads are verified.
            if (asset.sha256.isEmpty()) {
                val file = localPathFor(asset)
                if (file.exists() && file.length() > 0) {
                    val computed = sha256(file)
                    shaStore.setProperty(asset.id, computed)
                    saveShaStore()
                    Log.i(TAG, "Persisted SHA-256 for ${asset.id}: $computed")
                }
            }
        }
        val path = localPathFor(asset).absolutePath
        val deviceId = if (asset.kind == AiModelKind.MASK_SEGMENT) OnnxModelManager.DEVICE_CPU
        else OnnxModelManager.DEVICE_NNAPI
        // Pass the asset id so handleFor(asset.id) resolves to this session.
        val handle = onnxModelManager.loadModel(path, deviceId, asset.id)
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
