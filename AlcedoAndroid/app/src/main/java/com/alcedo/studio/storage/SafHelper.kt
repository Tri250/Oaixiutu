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

    // Supported image extensions for directory import filtering
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "tiff", "tif", "arw", "cr2", "cr3", "nef", "dng",
        "heic", "heif", "webp", "bmp", "gif", "exr",
        "orf", "pef", "srw", "x3f", "raf", "rw2", "mos"
    )

    fun isImageFile(fileName: String, mimeType: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS ||
            mimeType.startsWith("image/") ||
            mimeType.startsWith("image/x-") ||
            mimeType == "image/dng"
    }

    // List files in a SAF directory (non-recursive)
    fun listDirectory(context: Context, treeUri: Uri): List<SafFileInfo> {
        val files = mutableListOf<SafFileInfo>()
        val resolver = context.contentResolver

        // CRITICAL: For tree URIs returned by OpenDocumentTree, we must use
        // getTreeDocumentId() — not getDocumentId(). getDocumentId() returns
        // null for tree URIs because the path segment is "tree/..." not
        // "document/...", which produces a malformed children URI and an
        // empty query result (the root cause of "no photos imported").
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tree document ID", e)
            return emptyList()
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

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

    // Recursively list all image files in a SAF directory tree
    fun listImageFilesRecursive(context: Context, treeUri: Uri): List<SafFileInfo> {
        val imageFiles = mutableListOf<SafFileInfo>()
        val visitedDirs = mutableSetOf<String>()
        listImageFilesRecursiveInternal(context, treeUri, treeUri, imageFiles, visitedDirs, maxDepth = 10)
        return imageFiles
    }

    private fun listImageFilesRecursiveInternal(
        context: Context,
        treeUri: Uri,
        currentDirUri: Uri,
        results: MutableList<SafFileInfo>,
        visitedDirs: MutableSet<String>,
        maxDepth: Int
    ) {
        if (maxDepth <= 0) return

        val currentDocId = try {
            if (currentDirUri == treeUri) {
                DocumentsContract.getTreeDocumentId(treeUri)
            } else {
                DocumentsContract.getDocumentId(currentDirUri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get document ID for $currentDirUri", e)
            return
        }

        if (!visitedDirs.add(currentDocId)) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val displayName = cursor.getString(1) ?: ""
                    val mimeType = cursor.getString(2) ?: ""
                    val size = cursor.getLong(3)
                    val lastModified = cursor.getLong(4)

                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                    if (isDir) {
                        listImageFilesRecursiveInternal(
                            context, treeUri, documentUri, results, visitedDirs, maxDepth - 1
                        )
                    } else if (isImageFile(displayName, mimeType)) {
                        results.add(SafFileInfo(
                            uri = documentUri,
                            name = displayName,
                            mimeType = mimeType,
                            size = size,
                            lastModified = lastModified,
                            isDirectory = false,
                            isRaw = MediaStoreHelper.isRawFile(displayName, mimeType)
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list directory $currentDirUri", e)
        }
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
            // 对 OpenDocumentTree 返回的 tree URI 必须用 getTreeDocumentId,
            // getDocumentId 对 tree URI 返回 null (路径段是 "tree/..." 而非 "document/...")
            // 此 bug 与 listDirectory 相同,会导致 createDocument 失败 (导出致命问题 1 修复)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
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
        // 循环替换 ".." 直到不再出现，防止 "..." → "._" 而非 "_" 的情况
        var sanitized = name
            .replace("\\", "_")
            .replace(":", "_")
            .replace("|", "_")
        while (sanitized.contains("..")) {
            sanitized = sanitized.replace("..", "_")
        }
        sanitized = sanitized.replace("/", "_")
        return sanitized.trim()
    }
}
