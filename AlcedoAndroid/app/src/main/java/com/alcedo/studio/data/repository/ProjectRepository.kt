package com.alcedo.studio.data.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alcedo.studio.data.local.ProjectEntity
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.model.Project
import com.alcedo.studio.domain.repository.ProjectRepository
import com.alcedo.studio.utils.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ProjectRepository]. Projects are persisted in
 * the [ProjectEntity] table; because no dedicated DAO is exposed, raw queries
 * run through the database's [SupportSQLiteDatabase] helper. A [MutableStateFlow]
 * mirrors the table so the UI can observe changes reactively.
 */
@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val database: SleeveDatabase,
) : ProjectRepository {

    private val db: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    private val projectsFlow = _projects.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        _projects.value = queryAll()
    }

    override fun observeProjects(): Flow<List<Project>> = projectsFlow

    override fun observeProject(id: String): Flow<Project?> =
        kotlinx.coroutines.flow.flow {
            emit(getProject(id))
        }

    override suspend fun getProject(id: String): Project? = queryById(id)

    override suspend fun listProjects(): List<Project> = queryAll()

    override suspend fun createProject(name: String, filePath: String, rootSleeveId: String): Project {
        val now = System.currentTimeMillis()
        val project = Project(
            id = IdGenerator.newId("prj"),
            name = name,
            filePath = filePath,
            rootSleeveId = rootSleeveId,
            createdAt = now,
            modifiedAt = now,
        )
        val cv = ContentValues().apply {
            put("id", project.id)
            put("name", project.name)
            put("filePath", project.filePath)
            put("rootSleeveId", project.rootSleeveId)
            put("createdAt", project.createdAt)
            put("modifiedAt", project.modifiedAt)
            put("description", project.description)
            put("version", project.version)
            put("schemaVersion", project.schemaVersion)
            put("imageCount", 0)
            put("totalSizeBytes", 0L)
            put("thumbnailPath", null as String?)
            put("tags", "")
            put("isFavorite", 0)
            put("lastOpenedAt", null as Long?)
        }
        db.insert("projects", SQLiteDatabase.CONFLICT_REPLACE, cv)
        refresh()
        return project
    }

    override suspend fun updateProject(project: Project) {
        val cv = toContentValues(project)
        db.update("projects", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = ?", arrayOf(project.id))
        refresh()
    }

    override suspend fun deleteProject(id: String) {
        db.delete("projects", "id = ?", arrayOf(id))
        refresh()
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        val cv = ContentValues().apply {
            put("isFavorite", if (favorite) 1 else 0)
            put("modifiedAt", System.currentTimeMillis())
        }
        db.update("projects", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = ?", arrayOf(id))
        refresh()
    }

    override suspend fun touchLastOpened(id: String) {
        val cv = ContentValues().apply { put("lastOpenedAt", System.currentTimeMillis()) }
        db.update("projects", SQLiteDatabase.CONFLICT_REPLACE, cv, "id = ?", arrayOf(id))
        refresh()
    }

    override suspend fun incrementImageCount(id: String, delta: Int) {
        db.execSQL(
            "UPDATE projects SET imageCount = MAX(0, imageCount + ?), modifiedAt = ? WHERE id = ?",
            arrayOf<Any>(delta, System.currentTimeMillis(), id),
        )
        refresh()
    }

    override suspend fun addSize(id: String, bytes: Long) {
        db.execSQL(
            "UPDATE projects SET totalSizeBytes = totalSizeBytes + ?, modifiedAt = ? WHERE id = ?",
            arrayOf<Any>(bytes, System.currentTimeMillis(), id),
        )
        refresh()
    }

    private fun queryAll(): List<Project> {
        val results = mutableListOf<Project>()
        db.query("SELECT * FROM projects ORDER BY modifiedAt DESC").use { c ->
            while (c.moveToNext()) results.add(cursorToProject(c))
        }
        return results
    }

    private fun queryById(id: String): Project? =
        db.query("SELECT * FROM projects WHERE id = ? LIMIT 1", arrayOf(id)).use { c ->
            if (c.moveToFirst()) cursorToProject(c) else null
        }

    private fun cursorToProject(c: android.database.Cursor): Project {
        fun str(col: String): String? = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getString(it) }
        fun long(col: String): Long = c.getColumnIndex(col).takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
        fun int(col: String): Int = c.getColumnIndex(col).takeIf { it >= 0 }?.let { c.getInt(it) } ?: 0
        return Project(
            id = str("id") ?: "",
            name = str("name") ?: "",
            filePath = str("filePath") ?: "",
            rootSleeveId = str("rootSleeveId") ?: "",
            createdAt = long("createdAt"),
            modifiedAt = long("modifiedAt"),
            description = str("description") ?: "",
            version = int("version"),
            schemaVersion = int("schemaVersion"),
            imageCount = int("imageCount"),
            totalSizeBytes = long("totalSizeBytes"),
            thumbnailPath = str("thumbnailPath"),
            tags = str("tags")?.split("\u0001")?.filter { it.isNotEmpty() } ?: emptyList(),
            isFavorite = int("isFavorite") == 1,
            lastOpenedAt = c.getColumnIndex("lastOpenedAt").takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) },
        )
    }

    private fun toContentValues(p: Project): ContentValues = ContentValues().apply {
        put("id", p.id)
        put("name", p.name)
        put("filePath", p.filePath)
        put("rootSleeveId", p.rootSleeveId)
        put("createdAt", p.createdAt)
        put("modifiedAt", p.modifiedAt)
        put("description", p.description)
        put("version", p.version)
        put("schemaVersion", p.schemaVersion)
        put("imageCount", p.imageCount)
        put("totalSizeBytes", p.totalSizeBytes)
        put("thumbnailPath", p.thumbnailPath)
        put("tags", p.tags.joinToString("\u0001"))
        put("isFavorite", if (p.isFavorite) 1 else 0)
        put("lastOpenedAt", p.lastOpenedAt)
    }
}
