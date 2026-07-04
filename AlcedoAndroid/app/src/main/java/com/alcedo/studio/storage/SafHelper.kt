package com.alcedo.studio.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.InputStream
import java.io.OutputStream

object SafHelper {
    private const val TAG = "SafHelper"

    // Check if we have persistent URI permission for a directory
    fun hasPersistentPermission(context: Context, uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri
        }
    }

    // List files in a SAF directory
    fun listDirectory(context: Context, treeUri: Uri): List<SafFileInfo> {
        val files = mutableListOf<SafFileInfo>()
        val resolver = context.contentResolver

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getDocumentId(treeUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val displayName = cursor.getString(1) ?: ""
                    val mimeType = cursor.getString(2) ?: ""
                    val size = cursor.getLong(3)
                    val lastModified = cursor.getLong(4)

                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                    files.add(SafFileInfo(
                        uri = documentUri,
                        name = displayName,
                        mimeType = mimeType,
                        size = size,
                        lastModified = lastModified,
                        isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                        isRaw = MediaStoreHelper.isRawFile(displayName, mimeType)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list SAF directory", e)
        }

        return files
    }

    data class SafFileInfo(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean,
        val isRaw: Boolean
    )

    // Open input stream from SAF URI
    fun openInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SAF input stream", e)
            null
        }
    }

    // Open output stream to SAF URI
    fun openOutputStream(context: Context, uri: Uri): OutputStream? {
        return try {
            context.contentResolver.openOutputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SAF output stream", e)
            null
        }
    }

    // Create a new file in a SAF directory
    fun createFile(context: Context, treeUri: Uri, fileName: String, mimeType: String): Uri? {
        if (!isPathSafe(fileName)) {
            Log.e(TAG, "Unsafe file name rejected: $fileName")
            return null
        }
        return try {
            val docId = DocumentsContract.getDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, sanitizeFileName(fileName))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create file via SAF", e)
            null
        }
    }

    // ── Path Validation ──

    fun isPathSafe(path: String): Boolean {
        // Prevent path traversal attacks
        if (path.contains("..")) return false
        if (path.contains("//")) return false
        if (path.startsWith("/")) return false // Relative paths only
        return true
    }

    fun sanitizeFileName(name: String): String {
        // Remove any path separators or dangerous characters
        return name.replace("/", "_")
            .replace("\\", "_")
            .replace("..", "_")
            .replace(":", "_")
            .replace("|", "_")
            .trim()
    }
}
