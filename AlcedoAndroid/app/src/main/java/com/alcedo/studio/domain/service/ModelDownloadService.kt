package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.data.model.AiModelAsset
import com.alcedo.studio.security.SecureHttpClient
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads AI model assets with SHA-256 verification and resumable streaming.
 * Reports per-model progress for the model-manager screen.
 */
@Singleton
class ModelDownloadService @Inject constructor(
    private val httpClient: SecureHttpClient,
) {

    data class DownloadProgress(
        val modelId: String,
        val bytesRead: Long,
        val totalBytes: Long,
        val done: Boolean,
        val error: String? = null,
    )

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    /** Download [asset] to [destFile], verifying its SHA-256. Returns success. */
    suspend fun download(asset: AiModelAsset, destFile: File): Boolean = withContext(ThreadPool.io) {
        runCatching {
            destFile.parentFile?.mkdirs()
            val tmp = File(destFile.parentFile, "${destFile.name}.part")
            val request = Request.Builder().url(asset.downloadUrl).build()
            httpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _progress.value = DownloadProgress(asset.id, 0, asset.sizeBytes, false, "HTTP ${response.code}")
                    return@use false
                }
                val body = response.body ?: return@use false
                val total = body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var bytes = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytes += read
                            _progress.value = DownloadProgress(asset.id, bytes, total, false)
                        }
                    }
                }
            }
            // Verify SHA-256.
            val actualSha = sha256(tmp)
            if (!actualSha.equals(asset.sha256, ignoreCase = true)) {
                Log.w(TAG, "SHA mismatch for ${asset.id}: $actualSha != ${asset.sha256}")
                tmp.delete()
                _progress.value = DownloadProgress(asset.id, 0, asset.sizeBytes, false, "sha_mismatch")
                return@runCatching false
            }
            tmp.renameTo(destFile)
            _progress.value = DownloadProgress(asset.id, destFile.length(), asset.sizeBytes, true)
            true
        }.onFailure {
            Log.e(TAG, "download failed for ${asset.id}", it)
            _progress.value = DownloadProgress(asset.id, 0, asset.sizeBytes, false, it.message)
            false
        }.getOrDefault(false)
    }

    /** Delete a downloaded model file. */
    fun delete(asset: AiModelAsset, destFile: File): Boolean = destFile.delete()

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) md.update(buffer, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ModelDownloadService"
    }
}
