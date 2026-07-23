package com.alcedo.studio.ui.editor

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copy a LUT file from a content URI to the app's internal storage and return
 * the absolute path. Returns null if the copy fails.
 * Validates the file extension is .cube for LUTs.
 */
internal fun copyLutToInternal(context: Context, uri: Uri): String? {
    return try {
        val lutsDir = File(context.filesDir, "luts")
        if (!lutsDir.exists()) lutsDir.mkdirs()

        val fileName = uri.lastPathSegment?.substringAfterLast('/')
            ?: "lut_${System.currentTimeMillis()}.cube"

        // Validate file extension is .cube
        if (!fileName.lowercase().endsWith(".cube")) {
            return null
        }

        val outFile = File(lutsDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null

        outFile.absolutePath
    } catch (e: Exception) {
        android.util.Log.e("LutUtils", "Failed to copy LUT file: ${e.message}")
        null
    }
}