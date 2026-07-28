package com.alcedo.studio.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.alcedo.studio.util.ContextProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * SAF (Storage Access Framework) helpers. Reads/writes files via content URIs
 * (document trees, single documents) for user-chosen directories that aren't
 * covered by MediaStore. Used for export destinations and project folders.
 */
object SafHelper {

    /** List child document files under a tree [treeUri]. */
    fun listChildren(treeUri: Uri, context: Context = ContextProvider.requireContext()): List<DocumentFile> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return tree.listFiles().toList()
    }

    /** Create a file inside a tree directory. */
    fun createFile(treeUri: Uri, displayName: String, mimeType: String, context: Context = ContextProvider.requireContext()): DocumentFile? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        return tree.createFile(mimeType, displayName)
    }

    /** Copy a content [uri] into a local [dest] file. Returns bytes copied. */
    fun copyToLocal(uri: Uri, dest: File, context: Context = ContextProvider.requireContext()): Long {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: 0L
            dest.length()
        }.onFailure { Log.w(TAG, "copyToLocal failed", it) }.getOrDefault(0L)
    }

    /** Read a content [uri] fully into a byte array. */
    fun readBytes(uri: Uri, context: Context = ContextProvider.requireContext()): ByteArray? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.onFailure { Log.w(TAG, "readBytes failed", it) }.getOrNull()
    }

    /** Open an input stream for [uri], or null. */
    fun openInputStream(uri: Uri, context: Context = ContextProvider.requireContext()): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    /** Write [bytes] to a document [uri], creating it if needed. */
    fun writeBytes(uri: Uri, bytes: ByteArray, context: Context = ContextProvider.requireContext()): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes); true } ?: false
        }.onFailure { Log.w(TAG, "writeBytes failed", it) }.getOrDefault(false)
    }

    /** Persist permission for a tree URI so it survives process death. */
    fun persistTreePermission(treeUri: Uri, context: Context = ContextProvider.requireContext()) {
        runCatching {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }.onFailure { Log.w(TAG, "persist permission failed", it) }
    }

    private const val TAG = "SafHelper"
}
