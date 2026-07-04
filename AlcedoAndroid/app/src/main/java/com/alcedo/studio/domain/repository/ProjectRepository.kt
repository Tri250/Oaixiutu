package com.alcedo.studio.domain.repository

import android.content.ContentValues
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface ProjectRepository {
    suspend fun createProject(project: Project)
    suspend fun getProject(projectId: String): Project?
    suspend fun getAllProjects(): List<Project>
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(projectId: String)
}

class ProjectRepositoryImpl(private val db: SleeveDatabase) : ProjectRepository {

    override suspend fun createProject(project: Project) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("project_id", project.projectId)
            put("project_name", project.projectName)
            put("project_path", project.projectPath)
            put("sleeve_root_id", project.sleeveRootId.toInt())
            put("created_at", project.createdAt.toEpochMilli())
            put("modified_at", project.modifiedAt.toEpochMilli())
            put("metadata_json", project.metadata?.toString())
        }
        db.writableDatabase.insertWithOnConflict("projects", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    override suspend fun getProject(projectId: String): Project? = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            "projects", null, "project_id = ?",
            arrayOf(projectId), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Project(
                    projectId = cursor.getString(0),
                    projectName = cursor.getString(1),
                    projectPath = cursor.getString(2),
                    sleeveRootId = cursor.getInt(3).toUInt()
                )
            } else null
        }
    }

    override suspend fun getAllProjects(): List<Project> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Project>()
        db.readableDatabase.query("projects", null, null, null, null, null, "created_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                list.add(Project(
                    projectId = cursor.getString(0),
                    projectName = cursor.getString(1),
                    projectPath = cursor.getString(2),
                    sleeveRootId = cursor.getInt(3).toUInt()
                ))
            }
        }
        list
    }

    override suspend fun updateProject(project: Project) = createProject(project)

    override suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("projects", "project_id = ?", arrayOf(projectId))
    }
}
