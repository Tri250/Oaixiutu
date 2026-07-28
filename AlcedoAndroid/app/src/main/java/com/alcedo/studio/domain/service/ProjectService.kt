package com.alcedo.studio.domain.service

import android.util.Log
import com.alcedo.studio.data.model.Project
import com.alcedo.studio.domain.repository.ProjectRepository
import com.alcedo.studio.utils.IdGenerator
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Project management service. Creates/opens/saves .alcd projects, tracks the
 * active project, and coordinates with [ProjectPackageService] for archival.
 */
@Singleton
class ProjectService @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val packageService: ProjectPackageService,
) {

    @Volatile
    private var activeProject: Project? = null

    /** The currently open project, or null. */
    fun activeProject(): Project? = activeProject

    fun observeProjects(): Flow<List<Project>> = projectRepository.observeProjects()

    suspend fun listProjects(): List<Project> = projectRepository.listProjects()

    /** Create a new project rooted at [rootSleeveId]. */
    suspend fun create(name: String, rootSleeveId: String, baseDir: File): Project = withContext(ThreadPool.database) {
        val file = File(baseDir, "${name}.${Project.FILE_EXTENSION}")
        val project = projectRepository.createProject(name, file.absolutePath, rootSleeveId)
        packageService.createEmpty(project)
        activeProject = project
        project
    }

    /** Open an existing .alcd file by id (already tracked in the catalog). */
    suspend fun open(id: String): Project? = withContext(ThreadPool.database) {
        val project = projectRepository.getProject(id) ?: return@withContext null
        if (packageService.open(project)) {
            projectRepository.touchLastOpened(id)
            activeProject = project
            project
        } else null
    }

    /** Open an arbitrary .alcd file path, registering it in the catalog. */
    suspend fun openPath(path: String, rootSleeveId: String): Project? = withContext(ThreadPool.database) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val name = file.nameWithoutExtension
        val project = projectRepository.createProject(name, file.absolutePath, rootSleeveId)
        if (packageService.open(project)) {
            activeProject = project
            project
        } else {
            projectRepository.deleteProject(project.id)
            null
        }
    }

    /** Save the active project back to its .alcd file. */
    suspend fun save(): Boolean = withContext(ThreadPool.database) {
        val project = activeProject ?: return@withContext false
        val ok = packageService.save(project)
        if (ok) projectRepository.updateProject(project.copy(modifiedAt = System.currentTimeMillis()))
        ok
    }

    suspend fun close() {
        activeProject = null
    }

    suspend fun delete(id: String) {
        projectRepository.deleteProject(id)
        if (activeProject?.id == id) activeProject = null
    }

    suspend fun setFavorite(id: String, favorite: Boolean) =
        projectRepository.setFavorite(id, favorite)

    companion object {
        private const val TAG = "ProjectService"
    }
}
