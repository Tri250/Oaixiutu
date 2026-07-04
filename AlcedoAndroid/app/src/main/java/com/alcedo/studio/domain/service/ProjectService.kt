package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.ProjectRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProjectService(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val imageRepository: ImageRepository,
    private val sleeveRepository: SleeveRepository,
    private val editHistoryRepository: EditHistoryRepository,
    private val projectPackageService: ProjectPackageService
) {
    sealed class ProjectState {
        data object Idle : ProjectState()
        data class Loading(val projectId: String) : ProjectState()
        data class Ready(val project: Project) : ProjectState()
        data class Error(val message: String) : ProjectState()
        data class Saving(val projectId: String) : ProjectState()
        data class Saved(val project: Project) : ProjectState()
    }

    private val _projectState = MutableStateFlow<ProjectState>(ProjectState.Idle)
    val projectState: StateFlow<ProjectState> = _projectState.asStateFlow()

    private val _recentProjects = MutableStateFlow<List<RecentProject>>(emptyList())
    val recentProjects: StateFlow<List<RecentProject>> = _recentProjects.asStateFlow()

    fun getCurrentProject(): Project? = null
    fun getCurrentProjectId(): String? = null
    fun isProjectOpen(): Boolean = false
    fun markDirty() {}
    fun getRecentProjectsSync(): List<RecentProject> = _recentProjects.value
    fun shutdown() {}
}
