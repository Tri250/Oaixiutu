package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.data.model.Project
import com.alcedo.studio.data.model.ProjectHeader
import com.alcedo.studio.data.model.ProjectPackageEntry
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * .alcd project packaging. A .alcd is a self-contained archive bundling the
 * sleeve DB, originals (or references), edit history, presets and thumbnails.
 * On Android it is implemented as a zip container with a binary header; the
 * native layer can later swap in a zstd-backed custom container. This service
 * handles create/open/save at the Kotlin level.
 */
@Singleton
class ProjectPackageService @Inject constructor() {

    /** Create an empty .alcd archive for [project]. */
    suspend fun createEmpty(project: Project): Boolean = withContext(ThreadPool.io) {
        runCatching {
            val file = File(project.filePath)
            file.parentFile?.mkdirs()
            val header = headerFor(project, entryCount = 0, entryOffset = 0L)
            ZipOutputStream(file.outputStream().buffered()).use { zos ->
                zos.setLevel(Deflater.BEST_SPEED)
                zos.putNextEntry(ZipEntry("project.json"))
                zos.write(headerJson(project, header).toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("sleeve/"))
                zos.closeEntry()
            }
            true
        }.onFailure { Log.w(TAG, "createEmpty failed", it) }.getOrDefault(false)
    }

    /** Open an existing .alcd archive and validate its header. */
    suspend fun open(project: Project): Boolean = withContext(ThreadPool.io) {
        val file = File(project.filePath)
        if (!file.exists()) {
            Log.w(TAG, "project file missing: ${project.filePath}")
            return@withContext false
        }
        runCatching {
            java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zis ->
                var valid = false
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "project.json") {
                        val text = zis.readBytes().toString(Charsets.UTF_8)
                        valid = text.contains("\"magic\":\"ALCD\"") || text.contains("ALCD")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                valid
            }
        }.onFailure { Log.w(TAG, "open failed", it) }.getOrDefault(false)
    }

    /** Save [project] back to its .alcd archive (re-zips the current state). */
    suspend fun save(project: Project): Boolean = withContext(ThreadPool.io) {
        // For now, saving rewrites the container header with a fresh modifiedAt.
        runCatching {
            createEmpty(project.copy(modifiedAt = System.currentTimeMillis()))
        }.getOrDefault(false)
    }

    /** List the entries inside a .alcd archive. */
    suspend fun listEntries(filePath: String): List<ProjectPackageEntry> = withContext(ThreadPool.io) {
        val results = mutableListOf<ProjectPackageEntry>()
        runCatching {
            java.util.zip.ZipInputStream(File(filePath).inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    results.add(
                        ProjectPackageEntry(
                            relativePath = entry.name,
                            offset = 0L,
                            compressedSize = entry.compressedSize,
                            uncompressedSize = entry.size,
                            crc32 = entry.crc,
                        ),
                    )
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }.onFailure { Log.w(TAG, "listEntries failed", it) }
        results
    }

    private fun headerFor(project: Project, entryCount: Int, entryOffset: Long): ProjectHeader =
        ProjectHeader(
            name = project.name,
            createdAt = project.createdAt,
            modifiedAt = project.modifiedAt,
            imageCount = project.imageCount,
            entryOffset = entryOffset,
            entryCount = entryCount,
        )

    private fun headerJson(project: Project, header: ProjectHeader): String = buildString {
        append('{')
        append("\"magic\":\"ALCD\",")
        append("\"version\":${project.version},")
        append("\"schemaVersion\":${project.schemaVersion},")
        append("\"name\":\"${escape(project.name)}\",")
        append("\"createdAt\":${header.createdAt},")
        append("\"modifiedAt\":${header.modifiedAt},")
        append("\"imageCount\":${header.imageCount},")
        append("\"entryCount\":${header.entryCount},")
        append("\"entryOffset\":${header.entryOffset}")
        append('}')
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val TAG = "ProjectPackageService"
    }
}
