package com.alcedo.studio.domain.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant

interface ProjectRepository {
    suspend fun createProject(project: Project)
    suspend fun getProject(projectId: String): Project?
    suspend fun getProjectByPath(path: String): Project?
    suspend fun getAllProjects(): List<Project>
    suspend fun getRecentProjects(limit: Int = 10): List<Project>
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(projectId: String)
    suspend fun setProjectMetadata(projectId: String, metadata: kotlinx.serialization.json.JsonObject)
    suspend fun updateProjectModifiedTime(projectId: String)
    suspend fun getProjectCount(): Int
    suspend fun projectExists(projectId: String): Boolean
}

class ProjectRepositoryImpl(private val db: SleeveDatabase) : ProjectRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val writableDb get() = db.openHelper.writableDatabase
    private val readableDb get() = db.openHelper.readableDatabase

    override suspend fun createProject(project: Project) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("project_id", project.projectId)
            put("project_name", project.projectName)
            put("project_path", project.projectPath)
            put("sleeve_root_id", project.sleeveRootId.toInt())
            put("created_at", project.createdAt.toEpochMilli())
            put("modified_at", project.modifiedAt.toEpochMilli())
            put("metadata_json", project.metadata?.toString())
            put("thumbnail_cache_path", project.thumbnailCachePath)
            put("model_cache_path", project.modelCachePath)
            put("package_version", project.packageVersion)
            put("is_open", if (project.isOpen) 1 else 0)
        }
        writableDb.insertWithOnConflict(
            "projects", null, values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    override suspend fun getProject(projectId: String): Project? = withContext(Dispatchers.IO) {
        readableDb.query(
            "projects", null, "project_id = ?",
            arrayOf(projectId), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursorToProject(cursor)
            } else null
        }
    }

    override suspend fun getProjectByPath(path: String): Project? = withContext(Dispatchers.IO) {
        readableDb.query(
            "projects", null, "project_path = ?",
            arrayOf(path), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursorToProject(cursor)
            } else null
        }
    }

    override suspend fun getAllProjects(): List<Project> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Project>()
        readableDb.query(
            "projects", null, null, null, null, null, "modified_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToProject(cursor))
            }
        }
        list
    }

    override suspend fun getRecentProjects(limit: Int): List<Project> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Project>()
        readableDb.query(
            "projects", null, null, null, null, null,
            "modified_at DESC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToProject(cursor))
            }
        }
        list
    }

    override suspend fun updateProject(project: Project) = createProject(project)

    override suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        writableDb.delete("projects", "project_id = ?", arrayOf(projectId))
    }

    override suspend fun setProjectMetadata(
        projectId: String,
        metadata: kotlinx.serialization.json.JsonObject
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("metadata_json", metadata.toString())
            put("modified_at", Instant.now().toEpochMilli())
        }
        writableDb.update("projects", values, "project_id = ?", arrayOf(projectId))
    }

    override suspend fun updateProjectModifiedTime(projectId: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("modified_at", Instant.now().toEpochMilli())
        }
        writableDb.update("projects", values, "project_id = ?", arrayOf(projectId))
    }

    override suspend fun getProjectCount(): Int = withContext(Dispatchers.IO) {
        readableDb.rawQuery("SELECT COUNT(*) FROM projects", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    override suspend fun projectExists(projectId: String): Boolean = withContext(Dispatchers.IO) {
        getProject(projectId) != null
    }

    // ── Cursor Parsing ──

    private fun cursorToProject(cursor: android.database.Cursor): Project {
        val metadataStr = cursor.getString(cursor.getColumnIndexOrThrow("metadata_json"))
        val metadata = try {
            if (!metadataStr.isNullOrBlank()) {
                json.decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), metadataStr)
            } else null
        } catch (_: Exception) {
            null
        }

        return Project(
            projectId = cursor.getString(cursor.getColumnIndexOrThrow("project_id")),
            projectName = cursor.getString(cursor.getColumnIndexOrThrow("project_name")),
            projectPath = cursor.getString(cursor.getColumnIndexOrThrow("project_path")),
            sleeveRootId = cursor.getInt(cursor.getColumnIndexOrThrow("sleeve_root_id")).toUInt(),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                .let { Instant.ofEpochMilli(it) },
            modifiedAt = cursor.getLong(cursor.getColumnIndexOrThrow("modified_at"))
                .let { Instant.ofEpochMilli(it) },
            metadata = metadata,
            thumbnailCachePath = cursor.getString(cursor.getColumnIndexOrThrow("thumbnail_cache_path")) ?: "",
            modelCachePath = cursor.getString(cursor.getColumnIndexOrThrow("model_cache_path")) ?: "",
            packageVersion = cursor.getInt(cursor.getColumnIndexOrThrow("package_version")),
            isOpen = cursor.getInt(cursor.getColumnIndexOrThrow("is_open")) == 1
        )
    }
}