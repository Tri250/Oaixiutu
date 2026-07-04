package com.alcedo.studio.data.local

import android.graphics.Bitmap
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ThumbnailDiskCache(private val cacheDir: File) {

    enum class CacheFormat {
        JPEG, PNG, WEBP
    }

    init { cacheDir.mkdirs() }

    fun get(key: String): Bitmap? = null

    fun get(key: String, tier: Int, format: CacheFormat = CacheFormat.JPEG): Bitmap? = null

    fun put(key: String, bitmap: Bitmap) {}

    fun put(key: String, inputStream: InputStream): Boolean = false

    fun contains(key: String): Boolean = File(cacheDir, key).exists()

    fun remove(key: String): Boolean {
        val file = File(cacheDir, key)
        return file.delete()
    }

    fun clear() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    fun size(): Long = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun getFile(key: String): File = File(cacheDir, key)

    fun getOutputStream(key: String): OutputStream? = null

    fun evict(keepBytes: Long) {}

    fun evict(key: String, tier: Int) {}
}
