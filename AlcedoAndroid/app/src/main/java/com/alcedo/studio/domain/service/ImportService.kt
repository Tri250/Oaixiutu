package com.alcedo.studio.domain.service

import android.content.Context
import android.net.Uri
import com.alcedo.studio.data.local.ImageMetadataDao
import com.alcedo.studio.data.local.ThumbnailDiskCache
import java.io.File

class ImportService(
    private val context: Context,
    private val metadataDao: ImageMetadataDao,
    private val sleeveService: SleeveService,
    private val thumbnailDiskCache: ThumbnailDiskCache
) {

    suspend fun importFromUri(uri: Uri, targetDir: File): File? = null

    suspend fun importFromPath(sourcePath: String, targetDir: File): File? = null

    suspend fun importMultiple(uris: List<Uri>, targetDir: File): List<File> = emptyList()

    suspend fun importImage(uri: Uri) {}

    suspend fun importDirectory(uri: Uri) {}

    fun getSupportedFormats(): List<String> = listOf("jpg", "jpeg", "png", "tiff", "arw", "cr2", "cr3", "nef", "dng", "heic", "heif", "webp")
}
