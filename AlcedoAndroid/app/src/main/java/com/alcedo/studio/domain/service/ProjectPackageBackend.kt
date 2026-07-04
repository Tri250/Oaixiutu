package com.alcedo.studio.domain.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backend for project packaging (import/export as .alcedo archive).
 * Ported from desktop project_package_backend.cpp
 */
class ProjectPackageBackend(private val context: Context) {

    data class PackageManifest(
        val version: Int = 1,
        val projectName: String,
        val imageCount: Int,
        val createdAt: Long = System.currentTimeMillis()
    )

    suspend fun packageProject(
        projectDir: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val files = projectDir.walkTopDown().toList()
            val totalFiles = files.size

            ZipOutputStream(outputFile.outputStream()).use { zipOut ->
                files.forEachIndexed { index, file ->
                    if (file.isFile) {
                        val entryPath = file.relativeTo(projectDir).path
                        zipOut.putNextEntry(ZipEntry(entryPath))
                        file.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                    onProgress(index.toFloat() / totalFiles)
                }
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unpackageProject(
        archiveFile: File,
        outputDir: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            ZipInputStream(archiveFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                var totalEntries = 0
                var processedEntries = 0

                // Count entries first
                while (entry != null) { totalEntries++; entry = zipIn.nextEntry }

                // Reset and extract
                zipIn.close()
                ZipInputStream(archiveFile.inputStream()).use { zipIn2 ->
                    entry = zipIn2.nextEntry
                    while (entry != null) {
                        val outFile = File(outputDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        if (!entry.isDirectory) {
                            zipIn2.copyTo(outFile.outputStream())
                        }
                        processedEntries++
                        onProgress(processedEntries.toFloat() / totalEntries)
                        entry = zipIn2.nextEntry
                    }
                }
            }
            Result.success(outputDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun validateArchive(archiveFile: File): Boolean {
        return try {
            ZipInputStream(archiveFile.inputStream()).use { zip ->
                zip.nextEntry != null
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val ARCHIVE_EXTENSION = ".alcedo"
        const val MANIFEST_FILE = "manifest.json"
    }
}
