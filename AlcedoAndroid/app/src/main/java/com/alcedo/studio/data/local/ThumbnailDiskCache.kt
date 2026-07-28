package com.alcedo.studio.data.local

import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.IdGenerator
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Disk cache for generated thumbnails. Stores each thumbnail as a WebP/JPEG blob
 * keyed by a content hash of the source URI + target size. An in-memory index
 * tracks total bytes so the cache can be trimmed to [maxBytes].
 *
 * Mirrors the desktop core/app/thumbnail_disk_cache_service.
 */
class ThumbnailDiskCache(
    private val cacheDir: File = ContextProvider.requireContext().cacheDir.let {
        File(it, "thumbnails").apply { mkdirs() }
    },
    private val maxBytes: Long = 512L * 1024L * 1024L,
) {
    private val lock = ReentrantLock()

    init {
        cacheDir.mkdirs()
        trimToSize()
    }

    /** The absolute file for a cache key, whether or not it exists. */
    fun fileFor(key: String, ext: String = "webp"): File = File(cacheDir, "$key.$ext")

    /** True if a thumbnail for [sourceUri] at [size] is present on disk. */
    fun contains(sourceUri: String, size: Int): Boolean = lock.withLock {
        fileFor(keyFor(sourceUri, size)).exists()
    }

    /** Read a cached thumbnail as a decoded [Bitmap], or null on miss/decode error. */
    fun read(sourceUri: String, size: Int): Bitmap? = lock.withLock {
        val file = fileFor(keyFor(sourceUri, size))
        if (!file.exists()) return@withLock null
        runCatching {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        }.onFailure { Log.w(TAG, "decode failed for ${file.name}", it) }.getOrNull()
    }

    /** Write [bitmap] to the cache for [sourceUri] at [size]. */
    fun write(sourceUri: String, size: Int, bitmap: Bitmap, quality: Int = 85): Boolean =
        lock.withLock {
            val file = fileFor(keyFor(sourceUri, size))
            runCatching {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
                }
                trimToSize()
                true
            }.onFailure { Log.w(TAG, "write failed", it) }.getOrDefault(false)
        }

    /** Write a raw byte blob (already-encoded image) directly. */
    fun writeBytes(sourceUri: String, size: Int, bytes: ByteArray, ext: String): Boolean =
        lock.withLock {
            val file = fileFor(keyFor(sourceUri, size), ext)
            runCatching {
                FileOutputStream(file).use { it.write(bytes) }
                trimToSize()
                true
            }.onFailure { Log.w(TAG, "writeBytes failed", it) }.getOrDefault(false)
        }

    /** Remove a single thumbnail. */
    fun remove(sourceUri: String, size: Int) = lock.withLock {
        fileFor(keyFor(sourceUri, size)).delete()
    }

    /** Total bytes currently used by the cache. */
    fun sizeBytes(): Long = lock.withLock {
        cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /** Clear the entire cache. */
    fun clear() = lock.withLock {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /** Trim oldest files until under [maxBytes]. */
    fun trimToSize() {
        val files = cacheDir.listFiles()?.toMutableList() ?: return
        files.sortBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= maxBytes) break
            total -= f.length()
            f.delete()
        }
    }

    /** Content-addressed key for a source URI and target size. */
    fun keyFor(sourceUri: String, size: Int): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("$sourceUri|$size".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 32)
    }

    /** A short human-readable summary for the manage-space screen. */
    fun describe(): String {
        val count = cacheDir.listFiles()?.size ?: 0
        val mb = sizeBytes() / (1024.0 * 1024.0)
        return "$count files, %.1f MB".format(mb)
    }

    companion object {
        private const val TAG = "ThumbnailDiskCache"
    }
}
