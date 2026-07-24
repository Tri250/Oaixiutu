package com.alcedo.studio.domain.service

import android.content.Context
import android.util.Log
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import com.alcedo.studio.service.TaskNotificationHelper

/**
 * AI model download and activation service.
 *
 * Handles downloading ONNX-format AI models with progress tracking,
 * model activation/deactivation, version management, and storage management.
 */
class ModelDownloadService(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val MODELS_DIR = "ai_models"
        private const val CONNECT_TIMEOUT_SEC = 30L
        private const val READ_TIMEOUT_SEC = 60L
    }

    private val modelsDir = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _models = MutableStateFlow<List<ModelAsset>>(emptyList())
    val models: StateFlow<List<ModelAsset>> = _models.asStateFlow()

    // ── Download progress tracking (modelId → fraction 0..1) ──
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Default Model Catalog ──

    val defaultModels: List<ModelAsset> = listOf(
        ModelAsset(
            modelId = "jina-clip-v2",
            modelName = "Jina CLIP v2",
            modelType = AiModelType.CLIP,
            version = "2.0.0",
            fileSizeBytes = 350_000_000L,
            downloadUrl = "https://huggingface.co/jinaai/jina-clip-v2/resolve/main/onnx/model_quantized.onnx",
            description = "Lightweight multilingual CLIP model for image tagging and semantic search. Supports 89 languages. 512-dim embeddings.",
            embeddingDim = 512,
            requiredStorageBytes = 500_000_000L
        ),
        ModelAsset(
            modelId = "siglip2",
            modelName = "SigLIP 2",
            modelType = AiModelType.SIGLIP,
            version = "2.0.0",
            fileSizeBytes = 420_000_000L,
            downloadUrl = "https://huggingface.co/google/siglip2-so400m-patch14-384/resolve/main/onnx/model.onnx",
            description = "Google SigLIP 2 for zero-shot image classification. 384×384 input, 1152-dim embeddings.",
            embeddingDim = 1152,
            requiredStorageBytes = 600_000_000L
        ),
        ModelAsset(
            modelId = "mobileclip-s2",
            modelName = "MobileCLIP S2",
            modelType = AiModelType.CLIP,
            version = "1.0.0",
            fileSizeBytes = 180_000_000L,
            downloadUrl = "https://huggingface.co/apple/mobileclip-s2/resolve/main/onnx/model.onnx",
            description = "Apple MobileCLIP S2 optimized for on-device mobile inference. 256×256 input, 512-dim embeddings.",
            embeddingDim = 512,
            requiredStorageBytes = 300_000_000L
        ),
        ModelAsset(
            modelId = "mobilenet-v3-labels",
            modelName = "MobileNetV3 Label Classifier",
            modelType = AiModelType.CUSTOM,
            version = "1.0.0",
            fileSizeBytes = 25_000_000L,
            downloadUrl = "https://huggingface.co/google/mobilenet-v3-large/resolve/main/onnx/model.onnx",
            description = "Lightweight ImageNet label classifier for quick label suggestions. 224×224 input.",
            embeddingDim = 1280,
            requiredStorageBytes = 50_000_000L
        )
    )

    init {
        // Load initial state from disk
        scope.launch {
            refreshModelCatalog()
        }
    }

    // ── Model Catalog Management ──

    suspend fun refreshModelCatalog() {
        val existingModels = _models.value.associateBy { it.modelId }.toMutableMap()

        for (default in defaultModels) {
            val localFile = getModelFile(default.modelId)
            if (localFile.exists()) {
                if (localFile.length() == 0L) {
                    // File exists but is empty → corrupt, mark for re-download
                    Log.w(TAG, "Model ${default.modelId} file exists but is empty (0 bytes), marking as CORRUPT")
                    localFile.delete()
                    existingModels[default.modelId] = default.copy(
                        downloadStatus = ModelDownloadStatus.CORRUPT,
                        downloadProgress = 0f
                    )
                } else {
                    // Model exists on disk, update status
                    existingModels[default.modelId] = default.copy(
                        localPath = localFile.absolutePath,
                        downloadStatus = ModelDownloadStatus.DOWNLOADED,
                        downloadProgress = 1.0f
                    )
                }
            } else {
                existingModels.putIfAbsent(default.modelId, default)
            }
        }

        _models.value = existingModels.values.toList()
    }

    fun getModelFile(modelId: String): File {
        return File(modelsDir, "$modelId.onnx")
    }

    // ── Download ──

    suspend fun downloadModel(
        modelId: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val model = _models.value.find { it.modelId == modelId } ?: return false

        // Cancel any existing download for this model
        downloadJobs[modelId]?.cancel()

        val job = scope.launch {
            try {
                updateModel(modelId, model.copy(
                    downloadStatus = ModelDownloadStatus.DOWNLOADING,
                    downloadProgress = 0f
                ))

                // Update download progress StateFlow
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    this[modelId] = 0f
                }

                val url = model.downloadUrl
                val destFile = getModelFile(modelId)
                val tempFile = File(destFile.absolutePath + ".tmp")

                // Resume support: check for existing partial download
                val existingSize = if (tempFile.exists()) tempFile.length() else 0L
                var appendMode = false

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AlcedoStudio-Android/0.2.6")

                if (existingSize > 0) {
                    // Request remaining bytes from server
                    requestBuilder.header("Range", "bytes=$existingSize-")
                }

                val response = httpClient.newCall(requestBuilder.build()).execute()

                // Check if server supports Range requests
                val serverSupportsRange = response.code == 206
                if (existingSize > 0 && !serverSupportsRange) {
                    // Server doesn't support Range, start from scratch
                    tempFile.delete()
                }

                if (!response.isSuccessful && response.code != 206) {
                    updateModel(modelId, model.copy(downloadStatus = ModelDownloadStatus.FAILED))
                    TaskNotificationHelper.notifyModelDownloadFailed(context, modelId, "HTTP ${response.code}")
                    _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                        remove(modelId)
                    }
                    return@launch
                }

                val body = response.body ?: run {
                    updateModel(modelId, model.copy(downloadStatus = ModelDownloadStatus.FAILED))
                    TaskNotificationHelper.notifyModelDownloadFailed(context, modelId, "Empty response body")
                    _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                        remove(modelId)
                    }
                    return@launch
                }

                val contentLength = body.contentLength()
                val totalBytes = if (serverSupportsRange && existingSize > 0) {
                    // For partial content, total = existing + remaining
                    if (contentLength > 0) {
                        existingSize + contentLength
                    } else {
                        // Content-Length unknown; estimate from model metadata but cap
                        // at a reasonable limit to avoid wildly wrong progress.
                        Log.w(TAG, "Content-Length unknown for partial download of $modelId, using model metadata estimate")
                        model.fileSizeBytes.coerceAtLeast(existingSize)
                    }
                } else if (contentLength > 0) {
                    contentLength
                } else {
                    // Content-Length completely unknown; use model metadata as a best
                    // estimate but log a warning so the operator knows progress may be
                    // inaccurate.
                    Log.w(TAG, "Content-Length unknown for download of $modelId, progress estimation may be inaccurate")
                    model.fileSizeBytes
                }

                var downloadedBytes = if (serverSupportsRange && existingSize > 0) existingSize else 0L

                // Use appending sink for resume, regular sink for fresh download
                val sink = if (serverSupportsRange && existingSize > 0) {
                    appendMode = true
                    FileOutputStream(tempFile, true).sink().buffer()
                } else {
                    tempFile.sink().buffer()
                }

                sink.use { sinkBuf ->
                    body.source().use { source ->
                        val buffer = okio.Buffer()
                        var bytesRead: Long
                        while (source.read(buffer, 8192).also { bytesRead = it } != -1L) {
                            if (!isActive) {
                                // Keep temp file for resume
                                updateModel(modelId, model.copy(
                                    downloadStatus = ModelDownloadStatus.PAUSED
                                ))
                                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                                    remove(modelId)
                                }
                                return@launch
                            }
                            sinkBuf.write(buffer, bytesRead)
                            downloadedBytes += bytesRead
                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                            updateModel(modelId, model.copy(downloadProgress = progress))
                            // Update download progress StateFlow
                            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                                this[modelId] = progress
                            }
                            // Update notification bar progress
                            TaskNotificationHelper.notifyModelDownloadProgress(
                                context, modelId, (progress * 100).toInt(), 100
                            )
                        }
                    }
                }
                body.close()

                // Move temp to final
                tempFile.renameTo(destFile)

                // ── Post-download integrity check: verify file exists and size > 0 ──
                if (!destFile.exists() || destFile.length() == 0L) {
                    destFile.delete()
                    updateModel(modelId, model.copy(
                        downloadStatus = ModelDownloadStatus.FAILED,
                        downloadProgress = 0f
                    ))
                    TaskNotificationHelper.notifyModelDownloadFailed(context, modelId, "Downloaded file is missing or empty")
                    _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                        remove(modelId)
                    }
                    Log.e(TAG, "Integrity check failed for model $modelId: file missing or zero-length after rename")
                    return@launch
                }
                Log.i(TAG, "Integrity check passed for model $modelId: file size=${destFile.length()} bytes")

                // Verify checksum if provided
                if (model.checksum.isNotEmpty()) {
                    val actualChecksum = computeSha256(destFile)
                    if (!actualChecksum.equals(model.checksum, ignoreCase = true)) {
                        destFile.delete()
                        updateModel(modelId, model.copy(
                            downloadStatus = ModelDownloadStatus.FAILED,
                            downloadProgress = 0f
                        ))
                        TaskNotificationHelper.notifyModelDownloadFailed(context, modelId, "Checksum mismatch")
                        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                            remove(modelId)
                        }
                        Log.e(TAG, "Checksum mismatch for model $modelId")
                        return@launch
                    }
                    updateModel(modelId, model.copy(
                        downloadStatus = ModelDownloadStatus.VERIFIED,
                        downloadProgress = 1.0f,
                        localPath = destFile.absolutePath
                    ))
                } else {
                    updateModel(modelId, model.copy(
                        downloadStatus = ModelDownloadStatus.DOWNLOADED,
                        downloadProgress = 1.0f,
                        localPath = destFile.absolutePath,
                        downloadedVersion = model.version
                    ))
                }

                // Notify completion
                TaskNotificationHelper.notifyModelDownloadComplete(context, modelId)
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    remove(modelId)
                }
            } catch (e: CancellationException) {
                // Keep temp file for potential resume
                updateModel(modelId, model.copy(
                    downloadStatus = ModelDownloadStatus.PAUSED
                ))
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    remove(modelId)
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $modelId: ${e.message}")
                updateModel(modelId, model.copy(
                    downloadStatus = ModelDownloadStatus.FAILED,
                    downloadProgress = 0f
                ))
                TaskNotificationHelper.notifyModelDownloadFailed(context, modelId, e.message ?: "下载失败")
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                    remove(modelId)
                }
            }
        }

        downloadJobs[modelId] = job
        job.join()
        return _models.value.find { it.modelId == modelId }?.downloadStatus == ModelDownloadStatus.DOWNLOADED ||
               _models.value.find { it.modelId == modelId }?.downloadStatus == ModelDownloadStatus.VERIFIED
    }

    suspend fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
    }

    /**
     * Resume a paused download. Supports Range-request resume:
     * if a partial temp file exists and the server supports Range headers,
     * the download will resume from where it left off.
     */
    suspend fun resumeModelDownload(
        modelId: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        val model = _models.value.find { it.modelId == modelId } ?: return false
        if (model.downloadStatus != ModelDownloadStatus.PAUSED) return false

        // Re-download; downloadModel now supports Range headers and will
        // resume from the existing temp file if possible.
        return downloadModel(modelId, onProgress)
    }

    // ── Activation ──

    suspend fun activateModel(modelId: String): Boolean {
        val model = _models.value.find { it.modelId == modelId } ?: return false
        val localFile = getModelFile(modelId)
        if (!localFile.exists()) return false

        // Deactivate all other models of the same type
        val modelType = model.modelType
        val updated = _models.value.map { m ->
            if (m.modelType == modelType) {
                m.copy(isActive = m.modelId == modelId)
            } else m
        }
        _models.value = updated

        // Mark as activated
        updateModel(modelId, model.copy(
            isActive = true,
            downloadStatus = ModelDownloadStatus.ACTIVATED
        ))
        return true
    }

    suspend fun deactivateModel(modelId: String) {
        val model = _models.value.find { it.modelId == modelId } ?: return
        updateModel(modelId, model.copy(
            isActive = false,
            downloadStatus = ModelDownloadStatus.DOWNLOADED
        ))
    }

    fun getActiveModelId(modelType: AiModelType): String? {
        return _models.value.find { it.modelType == modelType && it.isActive }?.modelId
    }

    fun getActiveModel(): ModelAsset? {
        return _models.value.find { it.isActive }
    }

    /**
     * Mark a model as corrupt so it will be re-downloaded on next attempt.
     * Called when a downloaded model file fails to load at inference time.
     */
    fun markCorrupt(modelId: String) {
        val model = _models.value.find { it.modelId == modelId } ?: return
        val file = getModelFile(modelId)
        if (file.exists()) {
            file.delete()
            Log.w(TAG, "Deleted corrupt model file for $modelId")
        }
        updateModel(modelId, model.copy(
            downloadStatus = ModelDownloadStatus.CORRUPT,
            downloadProgress = 0f,
            localPath = ""
        ))
    }

    // ── Storage Management ──

    fun getStorageUsage(): Long {
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun getAvailableStorage(): Long {
        return context.filesDir.usableSpace
    }

    suspend fun deleteModel(modelId: String) {
        cancelDownload(modelId)

        // Deactivate first
        val model = _models.value.find { it.modelId == modelId }
        if (model?.isActive == true) {
            deactivateModel(modelId)
        }

        val file = getModelFile(modelId)
        if (file.exists()) file.delete()

        updateModel(modelId, (model ?: defaultModels.find { it.modelId == modelId })
            ?.copy(
                downloadStatus = ModelDownloadStatus.NOT_DOWNLOADED,
                downloadProgress = 0f,
                localPath = "",
                isActive = false
            )
            ?: return)
    }

    suspend fun clearAllModels() {
        for (model in _models.value) {
            cancelDownload(model.modelId)
        }
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()
        _models.value = defaultModels
    }

    // ── Internal Helpers ──

    private fun updateModel(modelId: String, updated: ModelAsset) {
        val current = _models.value.toMutableList()
        val idx = current.indexOfFirst { it.modelId == modelId }
        if (idx >= 0) {
            current[idx] = updated
        } else {
            current.add(updated)
        }
        _models.value = current
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}