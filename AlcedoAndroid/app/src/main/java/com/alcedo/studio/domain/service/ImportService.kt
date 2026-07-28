package com.alcedo.studio.domain.service

import android.net.Uri
import android.util.Log
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.SleeveConstants
import com.alcedo.studio.data.model.isRawExtension
import com.alcedo.studio.data.repository.SleeveRepositoryImpl
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import com.alcedo.studio.storage.MediaStoreHelper
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.IdGenerator
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image import service. Resolves source URIs (MediaStore / SAF / picker),
 * extracts dimensions, inserts an [ImageItem] and a sleeve file entry, and
 * kicks off thumbnail generation. Reports per-file progress via [progress].
 */
@Singleton
class ImportService @Inject constructor(
    private val imageRepository: ImageRepository,
    private val sleeveRepository: SleeveRepository,
    private val decodeService: DecodeService,
    private val thumbnailService: ThumbnailService,
) {

    data class ImportProgress(
        val completed: Int,
        val total: Int,
        val currentUri: String?,
        val errors: List<String> = emptyList(),
    )

    private val _progress = MutableStateFlow(ImportProgress(0, 0, null))
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    /** Import a batch of [uris] into [destFolderPath]. Returns the imported images. */
    suspend fun import(uris: List<Uri>, destFolderPath: String = SleeveConstants.DEFAULT_IMPORT_FOLDER): List<ImageItem> =
        withContext(ThreadPool.compute) {
            if (uris.isEmpty()) return@withContext emptyList()
            val folderPath = ensureFolder(destFolderPath)
            val results = mutableListOf<ImageItem>()
            val errors = mutableListOf<String>()
            _progress.value = ImportProgress(0, uris.size, null)

            uris.forEachIndexed { index, uri ->
                runCatching {
                    val item = importOne(uri, folderPath)
                    results.add(item)
                }.onFailure {
                    Log.w(TAG, "import failed for $uri", it)
                    errors.add("${uri.lastPathSegment}: ${it.message}")
                }
                _progress.value = ImportProgress(index + 1, uris.size, uri.toString(), errors)
            }
            results
        }

    private suspend fun importOne(uri: Uri, folderPath: String): ImageItem {
        val context = ContextProvider.requireContext()
        val displayName = MediaStoreHelper.displayName(context, uri)
            ?: uri.lastPathSegment ?: "image_${IdGenerator.shortId()}"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val isRaw = isRawExtension(ext)

        val metadata = decodeService.extractMetadata(uri)
        val width = metadata?.width ?: 0
        val height = metadata?.height ?: 0
        val sizeBytes = MediaStoreHelper.fileSize(context, uri) ?: 0L
        val now = System.currentTimeMillis()

        val imageId = IdGenerator.newId("img")
        val sleevePath = "${folderPath.trimEnd('/')}/$displayName"

        val item = ImageItem(
            id = imageId,
            sleevePath = sleevePath,
            originalUri = uri.toString(),
            displayName = displayName,
            fileExtension = ext,
            fileSizeBytes = sizeBytes,
            width = width,
            height = height,
            dateAddedEpoch = now,
            dateCapturedEpoch = metadata?.captureDateEpoch ?: now,
            rating = 0,
            flag = ImageFlag.NONE,
            isRaw = isRaw,
            isVirtualCopy = false,
        )
        imageRepository.upsert(item)
        sleeveRepository.importFile(folderPath, uri.toString(), displayName)

        // Kick off thumbnail generation (best-effort, non-blocking).
        launchThumbnail(uri, imageId)
        return item
    }

    private fun launchThumbnail(uri: Uri, imageId: String) {
        ThreadPool.appScope.launch {
            runCatching {
                val path = thumbnailService.generateForUri(uri, imageId)
                if (path != null) imageRepository.setThumbnailPath(imageId, path)
            }
        }
    }

    private suspend fun ensureFolder(name: String): String {
        val root = SleeveConstants.ROOT_PATH
        val path = if (name == root) root else "$root/$name"
        sleeveRepository.getFolder(path) ?: sleeveRepository.createFolder(root, name)
        return path
    }

    companion object {
        private const val TAG = "ImportService"
    }
}
