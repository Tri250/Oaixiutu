package com.alcedo.studio.security

import android.util.Log
import com.alcedo.studio.util.ContextProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temp file management. Allocates and tracks scratch files used during decode,
 * export and model downloads, ensuring they are cleaned up on a periodic basis
 * and on low memory. Prevents unbounded cache growth in the app's cache dir.
 */
@Singleton
class TempFileManager @Inject constructor() {

    private val tracked = ConcurrentHashMap.newKeySet<File>()
    private val tempRoot: File by lazy {
        File(ContextProvider.requireContext().cacheDir, "alcedo_temp").apply { mkdirs() }
    }

    /** Create a new tracked temp file with [prefix] and [suffix]. */
    fun create(prefix: String = "tmp", suffix: String = ".bin"): File {
        val file = File(tempRoot, "${prefix}_${UUID.randomUUID()}$suffix")
        file.createNewFile()
        tracked.add(file)
        return file
    }

    /** Create a tracked temp directory. */
    fun createDir(prefix: String = "dir"): File {
        val dir = File(tempRoot, "${prefix}_${UUID.randomUUID()}")
        dir.mkdirs()
        tracked.add(dir)
        return dir
    }

    /** Mark [file] as no longer needed; deletes it and untracks. */
    fun release(file: File) {
        tracked.remove(file)
        runCatching {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }.onFailure { Log.w(TAG, "release failed for ${file.name}", it) }
    }

    /** Delete all tracked temp files (called on low memory / app exit). */
    fun cleanupAll() {
        for (f in tracked) {
            runCatching {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }
        }
        tracked.clear()
    }

    /** Sweep orphaned files in the temp root older than [maxAgeMs]. */
    fun sweepOrphans(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        tempRoot.listFiles()?.forEach { f ->
            if (!tracked.contains(f) && f.lastModified() < cutoff) {
                runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }
            }
        }
    }

    /** Total bytes used by the temp root. */
    fun usedBytes(): Long = tempRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Human-readable summary for the manage-space screen. */
    fun describe(): String {
        val mb = usedBytes() / (1024.0 * 1024.0)
        return "${tracked.size} tracked files, %.1f MB".format(mb)
    }

    companion object {
        private const val TAG = "TempFileManager"
    }
}
