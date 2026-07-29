package com.alcedo.studio.data.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.SleeveConstants
import com.alcedo.studio.data.model.SleeveElement
import com.alcedo.studio.data.model.SleeveFile
import com.alcedo.studio.data.model.SleeveFolder
import com.alcedo.studio.data.model.SleeveTree
import com.alcedo.studio.data.local.PathResolver
import com.alcedo.studio.domain.repository.SleeveRepository
import com.alcedo.studio.utils.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [SleeveRepository]. The sleeve tree is stored
 * in the sleeve_elements table; a [MutableStateFlow] mirrors it so the folder
 * sidebar and grid observe mutations. The native DuckDB sleeve remains the
 * source of truth and is reconciled by [com.alcedo.studio.domain.service.SleeveService].
 */
@Singleton
class SleeveRepositoryImpl @Inject constructor(
    private val database: SleeveDatabase,
) : SleeveRepository {

    private val db: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    private val _tree = MutableStateFlow<SleeveTree>(emptyTree())
    private val treeFlow = _tree.asStateFlow()

    init {
        ensureRoot()
        refresh()
    }

    override fun observeChildren(folderPath: String): Flow<List<SleeveElement>> =
        kotlinx.coroutines.flow.flow {
            emit(listChildren(folderPath))
        }

    override fun observeTree(): Flow<SleeveTree> = treeFlow

    override suspend fun listChildren(folderPath: String): List<SleeveElement> {
        val path = PathResolver.normalise(folderPath)
        return queryChildren(path)
    }

    override suspend fun getTree(): SleeveTree {
        refresh()
        return _tree.value
    }

    override suspend fun getFolder(folderPath: String): SleeveFolder? {
        val path = PathResolver.normalise(folderPath)
        return db.query("SELECT * FROM sleeve_elements WHERE sleevePath = ? AND isFolder = 1 LIMIT 1", arrayOf(path))
            .use { c -> if (c.moveToFirst()) c.toFolder() else null }
    }

    override suspend fun getFile(sleevePath: String): SleeveFile? {
        val path = PathResolver.normalise(sleevePath)
        return db.query("SELECT * FROM sleeve_elements WHERE sleevePath = ? AND isFolder = 0 LIMIT 1", arrayOf(path))
            .use { c -> if (c.moveToFirst()) c.toFile() else null }
    }

    override suspend fun createFolder(parentPath: String, name: String): SleeveFolder {
        val now = System.currentTimeMillis()
        val parent = PathResolver.normalise(parentPath)
        val path = PathResolver.join(parent, name)
        val id = IdGenerator.newId("dir")
        val cv = ContentValues().apply {
            put("id", id)
            put("parentId", queryIdForPath(parent))
            put("name", name)
            put("sleevePath", path)
            put("isFolder", 1)
            put("createdAt", now)
            put("modifiedAt", now)
            put("childCount", 0)
            put("imageCount", 0)
            put("isSmartCollection", 0)
        }
        db.insert("sleeve_elements", SQLiteDatabase.CONFLICT_REPLACE, cv)
        incrementChildCount(parent, 1)
        refresh()
        return getFolder(path) ?: throw NoSuchElementException("Folder not found: $path")
    }

    override suspend fun importFile(parentPath: String, uri: String, name: String): SleeveFile {
        val now = System.currentTimeMillis()
        val parent = PathResolver.normalise(parentPath)
        val path = PathResolver.join(parent, name)
        val id = IdGenerator.newId("sfile")
        val cv = ContentValues().apply {
            put("id", id)
            put("parentId", queryIdForPath(parent))
            put("name", name)
            put("sleevePath", path)
            put("isFolder", 0)
            put("createdAt", now)
            put("modifiedAt", now)
            put("imageId", id)
        }
        db.insert("sleeve_elements", SQLiteDatabase.CONFLICT_REPLACE, cv)
        incrementChildCount(parent, 1)
        refresh()
        return getFile(path) ?: throw NoSuchElementException("File not found: $path")
    }

    override suspend fun moveElement(srcPath: String, destPath: String): Boolean {
        val src = PathResolver.normalise(srcPath)
        val dest = PathResolver.normalise(destPath)
        if (src == ROOT) return false
        val newName = PathResolver.name(dest)
        val newParent = PathResolver.parent(dest)
        val parentId = queryIdForPath(newParent)
        val cv = ContentValues().apply {
            put("parentId", parentId)
            put("name", newName)
            put("sleevePath", dest)
            put("modifiedAt", System.currentTimeMillis())
        }
        val rows = db.update("sleeve_elements", SQLiteDatabase.CONFLICT_REPLACE, cv, "sleevePath = ?", arrayOf(src))
        refresh()
        return rows > 0
    }

    override suspend fun deleteElement(path: String): Boolean {
        val p = PathResolver.normalise(path)
        if (p == ROOT) return false
        // Delete element and any descendants.
        db.delete("sleeve_elements", "sleevePath = ? OR sleevePath LIKE ?", arrayOf(p, "$p/%"))
        refresh()
        return true
    }

    override suspend fun renameElement(path: String, newName: String): SleeveElement? {
        val p = PathResolver.normalise(path)
        if (p == ROOT) return null
        val parent = PathResolver.parent(p)
        val newPath = PathResolver.join(parent, newName)
        return if (moveElement(p, newPath)) {
            db.query("SELECT * FROM sleeve_elements WHERE sleevePath = ?", arrayOf(newPath))
                .use { c -> if (c.moveToFirst()) c.toElement() else null }
        } else null
    }

    override suspend fun countFolders(): Int =
        db.query("SELECT COUNT(*) FROM sleeve_elements WHERE isFolder = 1").use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    override suspend fun countFiles(): Int =
        db.query("SELECT COUNT(*) FROM sleeve_elements WHERE isFolder = 0").use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    // ---- internals ----

    private fun ensureRoot() {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("id", "root")
            put("parentId", null as String?)
            put("name", "/")
            put("sleevePath", ROOT)
            put("isFolder", 1)
            put("createdAt", now)
            put("modifiedAt", now)
            put("childCount", 0)
            put("imageCount", 0)
            put("isSmartCollection", 0)
        }
        db.insert("sleeve_elements", SQLiteDatabase.CONFLICT_IGNORE, cv)
    }

    private fun refresh() {
        val root = db.query("SELECT * FROM sleeve_elements WHERE sleevePath = ? LIMIT 1", arrayOf(ROOT))
            .use { c -> if (c.moveToFirst()) c.toFolder() else emptyTree().root }
        val childrenMap = mutableMapOf<String, MutableList<SleeveElement>>()
        db.query("SELECT * FROM sleeve_elements ORDER BY isFolder DESC, name ASC").use { c ->
            while (c.moveToNext()) {
                val element = c.toElement()
                val parentId = c.getStringOrNull("parentId") ?: continue
                childrenMap.getOrPut(parentId) { mutableListOf() }.add(element)
            }
        }
        _tree.value = SleeveTree(root, childrenMap)
    }

    private fun queryChildren(path: String): List<SleeveElement> {
        val parentId = queryIdForPath(path) ?: return emptyList()
        val results = mutableListOf<SleeveElement>()
        db.query(
            "SELECT * FROM sleeve_elements WHERE parentId = ? ORDER BY isFolder DESC, name ASC",
            arrayOf(parentId),
        ).use { c ->
            while (c.moveToNext()) results.add(c.toElement())
        }
        return results
    }

    private fun queryIdForPath(path: String): String? =
        db.query("SELECT id FROM sleeve_elements WHERE sleevePath = ? LIMIT 1", arrayOf(path))
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun incrementChildCount(folderPath: String, delta: Int) {
        db.execSQL(
            "UPDATE sleeve_elements SET childCount = MAX(0, childCount + ?) WHERE sleevePath = ?",
            arrayOf<Any>(delta, folderPath),
        )
    }

    private fun android.database.Cursor.toFolder(): SleeveFolder {
        val now = System.currentTimeMillis()
        return SleeveFolder(
            id = getStringOrNull("id") ?: "",
            parentId = getStringOrNull("parentId"),
            name = getStringOrNull("name") ?: "/",
            sleevePath = getStringOrNull("sleevePath") ?: ROOT,
            createdAt = getLong("createdAt", now),
            modifiedAt = getLong("modifiedAt", now),
            childCount = getInt("childCount", 0),
            imageCount = getInt("imageCount", 0),
            isSmartCollection = getInt("isSmartCollection", 0) == 1,
        )
    }

    private fun android.database.Cursor.toFile(): SleeveFile {
        val now = System.currentTimeMillis()
        return SleeveFile(
            id = getStringOrNull("id") ?: "",
            parentId = getStringOrNull("parentId"),
            name = getStringOrNull("name") ?: "",
            sleevePath = getStringOrNull("sleevePath") ?: ROOT,
            createdAt = getLong("createdAt", now),
            modifiedAt = getLong("modifiedAt", now),
            imageId = getStringOrNull("imageId") ?: "",
            sourceUri = getStringOrNull("name") ?: "",
            fileSizeBytes = getLong("fileSizeBytes", 0L).takeIf { it > 0 } ?: 0L,
            mimeType = "image/*",
        )
    }

    private fun android.database.Cursor.toElement(): SleeveElement =
        if (getInt("isFolder", 1) == 1) toFolder() else toFile()

    private fun android.database.Cursor.getStringOrNull(col: String): String? {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun android.database.Cursor.getLong(col: String, default: Long): Long {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else default
    }

    private fun android.database.Cursor.getInt(col: String, default: Int): Int {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else default
    }

    private fun emptyTree(): SleeveTree {
        val now = System.currentTimeMillis()
        val root = SleeveFolder(
            id = "root",
            parentId = null,
            name = "/",
            sleevePath = ROOT,
            createdAt = now,
            modifiedAt = now,
        )
        return SleeveTree(root, emptyMap())
    }

    companion object {
        private const val ROOT = SleeveConstants.ROOT_PATH
        private val UNUSED_LABEL = ColorLabel.NONE // kept to satisfy ColorLabel import for future smart-folder colors
    }
}
