package com.alcedo.studio.domain.repository

import com.alcedo.studio.data.model.Project
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for Alcedo projects (.alcd files). Implemented by
 * [com.alcedo.studio.data.repository.ProjectRepositoryImpl].
 */
interface ProjectRepository {

    fun observeProjects(): Flow<List<Project>>
    fun observeProject(id: String): Flow<Project?>

    suspend fun getProject(id: String): Project?
    suspend fun listProjects(): List<Project>

    suspend fun createProject(name: String, filePath: String, rootSleeveId: String): Project
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(id: String)

    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun touchLastOpened(id: String)
    suspend fun incrementImageCount(id: String, delta: Int)
    suspend fun addSize(id: String, bytes: Long)
}
