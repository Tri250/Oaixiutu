package com.alcedo.studio.security

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object TempFileManager {
    private const val TAG = "TempFileManager"
    private val trackedFiles = ConcurrentHashMap<String, Long>()

    fun createTempFile(context: Context, prefix: String = "alcedo_", suffix: String = ".tmp"): File {
        val tempFile = File.createTempFile(prefix, suffix, context.cacheDir)
        trackedFiles[tempFile.absolutePath] = System.currentTimeMillis()
        return tempFile
    }

    fun cleanupFile(file: File) {
        try {
            if (file.exists()) {
                // Overwrite with zeros before deletion for secure wipe
                val fileLength = file.length()
                if (fileLength > 0 && fileLength < 10 * 1024 * 1024) { // Only for files < 10MB
                    file.outputStream().use { os ->
                        // 修复: 原使用 file.length().toInt() 会导致大文件溢出 (>2GB)
                        // 改用 Long 类型的 remaining 和安全的 chunk 大小计算
                        val chunkSize = minOf(fileLength, 4096L).toInt()
                        val zeros = ByteArray(chunkSize)
                        var remaining = fileLength
                        while (remaining > 0) {
                            val writeSize = minOf(remaining.toInt(), chunkSize)
                            os.write(zeros, 0, writeSize)
                            remaining -= writeSize
                        }
                    }
                }
                file.delete()
            }
            trackedFiles.remove(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup temp file: ${file.absolutePath}", e)
        }
    }

    fun cleanupAll(context: Context) {
        trackedFiles.keys.toList().forEach { path ->
            cleanupFile(File(path))
        }

        // Also clean up any orphaned temp files in cache
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("alcedo_") && it.name.endsWith(".tmp") }
            ?.forEach { file ->
                val age = System.currentTimeMillis() - file.lastModified()
                if (age > 24 * 60 * 60 * 1000) { // Older than 24 hours
                    cleanupFile(file)
                }
            }
    }

    fun cleanupOldFiles(context: Context, maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("alcedo_") }
            ?.forEach { file ->
                if (now - file.lastModified() > maxAgeMs) {
                    cleanupFile(file)
                }
            }
    }
}
