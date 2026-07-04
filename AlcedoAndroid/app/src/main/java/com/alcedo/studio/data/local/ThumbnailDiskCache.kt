package com.alcedo.studio.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

class ThumbnailDiskCache(private val cacheDir: File) {
    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    fun put(imageId: UInt, bitmap: Bitmap) {
        val file = File(cacheDir, "thumb_${imageId}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
    }

    fun get(imageId: UInt): Bitmap? {
        val file = File(cacheDir, "thumb_${imageId}.jpg")
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun contains(imageId: UInt): Boolean {
        return File(cacheDir, "thumb_${imageId}.jpg").exists()
    }

    fun evict(imageId: UInt) {
        File(cacheDir, "thumb_${imageId}.jpg").delete()
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun size(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
