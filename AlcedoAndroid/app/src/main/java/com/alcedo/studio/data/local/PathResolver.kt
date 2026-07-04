package com.alcedo.studio.data.local

import com.alcedo.studio.data.model.SleeveElementEntity
import com.alcedo.studio.data.model.SleeveFileEntity
import com.alcedo.studio.data.model.SleeveFolderEntity
import java.io.File

/**
 * Path resolver that converts between element IDs and file system paths.
 * Handles relative paths, symlinks, path normalization, and root path management.
 */
class PathResolver(
    private val elementDao: SleeveElementDao,
    private val fileDao: SleeveFileDao,
    private val folderDao: SleeveFolderDao
) {
    companion object {
        const val SEPARATOR = "/"
        private val INVALID_CHARS = setOf('/', '\u0000')
        private val MAX_PATH_LENGTH = 4096
    }

    private var rootPath: String = "/"

    fun setRootPath(path: String) {
        rootPath = normalizePath(path)
    }

    fun getRootPath(): String = rootPath

    // ================================================================
    // Element ID to Path
    // ================================================================

    suspend fun resolvePath(elementId: Long): String {
        val element = elementDao.getElementById(elementId) ?: return "$rootPath/unknown"
        val segments = mutableListOf<String>()
        var currentId: Long? = elementId

        // Walk up the tree
        while (currentId != null) {
            val current = elementDao.getElementById(currentId) ?: break
            segments.add(0, current.elementName)
            currentId = current.parentId
        }

        return "$rootPath/${segments.joinToString(SEPARATOR)}"
    }

    suspend fun resolvePathWithNames(elementId: Long): String {
        val file = fileDao.getFileByElementId(elementId)
        return if (file != null) {
            resolvePath(elementId)
        } else {
            resolvePath(elementId)
        }
    }

    // ================================================================
    // Path to Element ID
    // ================================================================

    suspend fun resolvePathToId(path: String): Long? {
        val normalized = normalizePath(path)
        if (normalized == rootPath) return null // root

        val relativePath = if (normalized.startsWith(rootPath)) {
            normalized.removePrefix(rootPath).trimStart('/')
        } else {
            normalized.trimStart('/')
        }

        if (relativePath.isEmpty()) return null

        val segments = relativePath.split(SEPARATOR).filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        var currentParentId: Long? = null // start from root

        for (i in segments.indices) {
            val name = segments[i]
            val element = if (currentParentId == null) {
                // Search root children
                val rootChildren = elementDao.getRootElements()
                rootChildren.find { it.elementName == name }
            } else {
                val children = elementDao.getChildrenByParentId(currentParentId)
                children.find { it.elementName == name }
            }

            if (element == null) return null

            if (i == segments.size - 1) {
                return element.elementId
            }
            currentParentId = element.elementId
        }

        return null
    }

    // ================================================================
    // Path normalization
    // ================================================================

    fun normalizePath(path: String): String {
        if (path.isEmpty()) return rootPath

        var normalized = path.replace("\\", SEPARATOR)

        // Collapse multiple slashes
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", SEPARATOR)
        }

        // Resolve . and ..
        val segments = normalized.split(SEPARATOR).filter { it.isNotEmpty() }
        val resolved = mutableListOf<String>()

        for (segment in segments) {
            when (segment) {
                "." -> continue
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(segment)
            }
        }

        val result = if (resolved.isEmpty()) "" else resolved.joinToString(SEPARATOR)
        return if (normalized.startsWith(SEPARATOR)) "$SEPARATOR$result" else result
    }

    // ================================================================
    // Relative path resolution
    // ================================================================

    suspend fun resolveRelative(baseElementId: Long, relativePath: String): String? {
        val basePath = resolvePath(baseElementId)
        val baseDir = if (basePath.endsWith(SEPARATOR)) basePath else basePath.substringBeforeLast(SEPARATOR)
        val fullPath = normalizePath("$baseDir/$relativePath")
        return fullPath
    }

    suspend fun resolveRelativeToId(baseElementId: Long, relativePath: String): Long? {
        val resolved = resolveRelative(baseElementId, relativePath) ?: return null
        return resolvePathToId(resolved)
    }

    // ================================================================
    // Symlink handling
    // ================================================================

    suspend fun resolveSymlink(elementId: Long): String? {
        val element = elementDao.getElementById(elementId) ?: return null
        if (element.elementType == 0) { // FILE
            val file = fileDao.getFileByElementId(elementId)
            val filePath = file?.filePath ?: return null
            val actualFile = File(filePath)
            return if (actualFile.exists()) {
                actualFile.canonicalPath
            } else {
                filePath
            }
        }
        return resolvePath(elementId)
    }

    suspend fun resolveSymlinkToId(elementId: Long): Long? {
        val resolvedPath = resolveSymlink(elementId) ?: return null
        return resolvePathToId(resolvedPath) ?: elementId // fallback to original
    }

    // ================================================================
    // Path utilities
    // ================================================================

    fun getParentPath(path: String): String {
        val normalized = normalizePath(path)
        return normalized.substringBeforeLast(SEPARATOR).ifEmpty { rootPath }
    }

    fun getFileName(path: String): String {
        val normalized = normalizePath(path)
        return normalized.substringAfterLast(SEPARATOR)
    }

    fun getExtension(path: String): String {
        val name = getFileName(path)
        return name.substringAfterLast('.', "")
    }

    suspend fun getParentId(elementId: Long): Long? {
        return elementDao.getElementById(elementId)?.parentId
    }

    fun isValidPath(path: String): Boolean {
        if (path.length > MAX_PATH_LENGTH) return false
        return path.none { it in INVALID_CHARS }
    }

    fun joinPaths(vararg parts: String): String {
        return normalizePath(parts.joinToString(SEPARATOR))
    }

    // ================================================================
    // Batch operations
    // ================================================================

    suspend fun resolvePaths(elementIds: List<Long>): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        for (id in elementIds) {
            result[id] = resolvePath(id)
        }
        return result
    }

    suspend fun resolveToIds(paths: List<String>): Map<String, Long?> {
        val result = mutableMapOf<String, Long?>()
        for (path in paths) {
            result[path] = resolvePathToId(path)
        }
        return result
    }

    // ================================================================
    // Path components
    // ================================================================

    suspend fun getPathComponents(elementId: Long): List<String> {
        val path = resolvePath(elementId)
        val relativePath = if (path.startsWith(rootPath)) {
            path.removePrefix(rootPath).trimStart('/')
        } else {
            path.trimStart('/')
        }
        return relativePath.split(SEPARATOR).filter { it.isNotEmpty() }
    }

    suspend fun exists(path: String): Boolean {
        return resolvePathToId(path) != null
    }

    suspend fun isDirectory(elementId: Long): Boolean {
        val element = elementDao.getElementById(elementId) ?: return false
        return element.elementType == 1
    }

    suspend fun isFile(elementId: Long): Boolean {
        val element = elementDao.getElementById(elementId) ?: return false
        return element.elementType == 0
    }
}