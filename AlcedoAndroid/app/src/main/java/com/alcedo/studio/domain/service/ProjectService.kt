package com.alcedo.studio.domain.service

import android.content.Context
import android.util.Log
import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.ProjectRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectService(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val imageRepository: ImageRepository,
    private val sleeveRepository: SleeveRepository,
    private val editHistoryRepository: EditHistoryRepository,
    private val projectPackageService: ProjectPackageService
) {
    companion object {
        private const val TAG = "ProjectService"
        private const val AUTO_SAVE_DEBOUNCE_MS = 3000L
        private const val MAX_RECENT_PROJECTS = 10
        private const val BACKUP_DIR = "backups"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val openProjects = ConcurrentHashMap<String, Project>()
    private var currentProjectId: String? = null
    private var autoSaveJob: Job? = null
    private var isDirty = false

    private val _projectState = MutableStateFlow<ProjectState>(ProjectState.Idle)
    val projectState: StateFlow<ProjectState> = _projectState.asStateFlow()

    private val _recentProjects = MutableStateFlow<List<RecentProject>>(emptyList())
    val recentProjects: StateFlow<List<RecentProject>> = _recentProjects.asStateFlow()

    sealed class ProjectState {
        data object Idle : ProjectState()
        data class Loading(val projectId: String) : ProjectState()
        data class Ready(val project: Project) : ProjectState()
        data class Error(val message: String) : ProjectState()
        data class Saving(val projectId: String) : ProjectState()
        data class Saved(val project: Project) : ProjectState()
    }

    // ── Project Lifecycle ──

    suspend fun createProject(name: String, path: String): Project = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val projectDir = File(path, name)
        if (!projectDir.exists()) projectDir.mkdirs()

        val projectFile = File(projectDir, "$name.alcd")
        val project = Project(
            projectId = projectId,
            projectName = name,
            projectPath = projectFile.absolutePath,
            createdAt = Instant.now(),
            modifiedAt = Instant.now(),
            isOpen = true
        )

        projectRepository.createProject(project)
        openProjects[projectId] = project
        currentProjectId = projectId
        addToRecentProjects(project)

        _projectState.value = ProjectState.Ready(project)
        Log.i(TAG, "Project created: $name at ${projectFile.absolutePath}")
        project
    }

    suspend fun openProject(projectPath: String): Project = withContext(Dispatchers.IO) {
        _projectState.value = ProjectState.Loading("")

        val file = File(projectPath)
        if (!file.exists()) {
            _projectState.value = ProjectState.Error("Project file not found: $projectPath")
            throw IllegalStateException("Project file not found: $projectPath")
        }

        if (!file.name.endsWith(Project.FILE_EXTENSION)) {
            _projectState.value = ProjectState.Error("Invalid project file format")
            throw IllegalStateException("Invalid project file format")
        }

        // Try to load from repository first
        val existing = projectRepository.getProjectByPath(projectPath)
        if (existing != null) {
            val opened = existing.copy(isOpen = true, modifiedAt = Instant.now())
            projectRepository.updateProject(opened)
            openProjects[existing.projectId] = opened
            currentProjectId = existing.projectId
            addToRecentProjects(opened)
            _projectState.value = ProjectState.Ready(opened)
            startAutoSave()
            return@withContext opened
        }

        // Deserialize from .alcd file
        try {
            val pkg = projectPackageService.deserializeProject(projectPath)
            val project = pkg.project.copy(isOpen = true, modifiedAt = Instant.now())
            projectRepository.createProject(project)
            openProjects[project.projectId] = project
            currentProjectId = project.projectId
            addToRecentProjects(project)
            _projectState.value = ProjectState.Ready(project)
            startAutoSave()
            project
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open project: ${e.message}", e)
            _projectState.value = ProjectState.Error("Failed to open project: ${e.message}")
            throw e
        }
    }

    suspend fun saveProject(): Project = withContext(Dispatchers.IO) {
        val projectId = currentProjectId ?: throw IllegalStateException("No project is open")
        val project = openProjects[projectId] ?: throw IllegalStateException("Project not found")

        _projectState.value = ProjectState.Saving(projectId)

        try {
            val updated = project.copy(
                modifiedAt = Instant.now(),
                lastAutoSaveTime = Instant.now(),
                projectFileSize = File(project.projectPath).length()
            )
            projectRepository.updateProject(updated)
            openProjects[projectId] = updated

            // Serialize to .alcd file
            saveProjectPackage(updated)

            isDirty = false
            _projectState.value = ProjectState.Saved(updated)
            Log.i(TAG, "Project saved: ${updated.projectName}")
            updated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save project: ${e.message}", e)
            _projectState.value = ProjectState.Error("Failed to save: ${e.message}")
            throw e
        }
    }

    suspend fun closeProject() = withContext(Dispatchers.IO) {
        val projectId = currentProjectId ?: return@withContext

        // Auto-save if dirty
        if (isDirty) {
            try { saveProject() } catch (_: Exception) {}
        }

        stopAutoSave()
        autoSaveJob?.cancel()
        autoSaveJob = null

        val project = openProjects.remove(projectId)
        project?.let {
            val closed = it.copy(isOpen = false)
            projectRepository.updateProject(closed)
        }

        currentProjectId = null
        isDirty = false
        _projectState.value = ProjectState.Idle
        Log.i(TAG, "Project closed")
    }

    fun markDirty() {
        isDirty = true
        debounceAutoSave()
    }

    // ── Auto-Save ──

    private fun startAutoSave() {
        // Auto-save is triggered by markDirty with debounce
    }

    private fun debounceAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            try {
                saveProject()
            } catch (e: Exception) {
                Log.w(TAG, "Auto-save failed: ${e.message}")
            }
        }
    }

    private fun stopAutoSave() {
        autoSaveJob?.cancel()
    }

    // ── Project Package Serialization ──

    private suspend fun saveProjectPackage(project: Project) = withContext(Dispatchers.IO) {
        try {
            val pkg = projectPackageService.serializeProject(
                project = project,
                sleeveRepository = sleeveRepository,
                editHistoryRepository = editHistoryRepository,
                imageRepository = imageRepository
            )
            projectPackageService.writePackageToFile(pkg, project.projectPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save project package: ${e.message}", e)
            throw e
        }
    }

    // ── Recent Projects ──

    private fun addToRecentProjects(project: Project) {
        val current = _recentProjects.value.toMutableList()
        current.removeAll { it.projectId == project.projectId }
        current.add(0, RecentProject(
            projectId = project.projectId,
            projectName = project.projectName,
            projectPath = project.projectPath,
            lastOpenedAt = Instant.now()
        ))
        if (current.size > MAX_RECENT_PROJECTS) {
            current.removeAt(current.size - 1)
        }
        _recentProjects.value = current
        persistRecentProjects()
    }

    suspend fun loadRecentProjects() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("alcedo_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("recent_projects", null) ?: return@withContext
        try {
            val list = kotlinx.serialization.json.Json.decodeFromString<List<RecentProject>>(json)
            _recentProjects.value = list
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load recent projects: ${e.message}")
            _recentProjects.value = emptyList()
        }
    }

    private fun persistRecentProjects() {
        val prefs = context.getSharedPreferences("alcedo_prefs", Context.MODE_PRIVATE)
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(RecentProject.serializer()),
            _recentProjects.value
        )
        prefs.edit().putString("recent_projects", json).apply()
    }

    fun getRecentProjectsSync(): List<RecentProject> = _recentProjects.value

    // ── Project Validation ──

    suspend fun validateProject(projectPath: String): ProjectValidation = withContext(Dispatchers.IO) {
        val errors = mutableListOf<ProjectValidationError>()
        val warnings = mutableListOf<String>()

        val file = File(projectPath)
        if (!file.exists()) {
            errors.add(ProjectValidationError.MISSING_FILE)
            return@withContext ProjectValidation(false, errors, warnings)
        }

        if (!file.name.endsWith(Project.FILE_EXTENSION)) {
            errors.add(ProjectValidationError.CORRUPTED_DATA)
            warnings.add("File does not have .alcd extension")
            return@withContext ProjectValidation(false, errors, warnings)
        }

        try {
            val pkg = projectPackageService.deserializeProject(projectPath)

            if (pkg.version > Project.CURRENT_PACKAGE_VERSION) {
                errors.add(ProjectValidationError.INCOMPATIBLE_VERSION)
                warnings.add("Package version ${pkg.version} is newer than supported ${Project.CURRENT_PACKAGE_VERSION}")
            }

            if (pkg.project.sleeveRootId == 0u) {
                warnings.add("No sleeve root defined")
            }

            // Check for orphaned references
            val imageIds = pkg.imageMetadata.map { it.imageId }.toSet()
            val historyImageIds = pkg.editHistories.keys
            for (hid in historyImageIds) {
                if (hid !in imageIds) {
                    warnings.add("Orphaned edit history for image $hid")
                }
            }

            return@withContext ProjectValidation(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings
            )
        } catch (e: Exception) {
            errors.add(ProjectValidationError.CORRUPTED_DATA)
            warnings.add("Failed to parse project: ${e.message}")
            return@withContext ProjectValidation(false, errors, warnings)
        }
    }

    // ── Project Backup / Restore ──

    suspend fun createBackup(): ProjectBackup = withContext(Dispatchers.IO) {
        val projectId = currentProjectId ?: throw IllegalStateException("No project is open")
        val project = openProjects[projectId] ?: throw IllegalStateException("Project not found")

        val backupDir = File(context.filesDir, BACKUP_DIR)
        if (!backupDir.exists()) backupDir.mkdirs()

        val backupId = UUID.randomUUID().toString()
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val backupName = "${project.projectName}_${timestamp}.alcd.bak"
        val backupFile = File(backupDir, backupName)

        // Copy the project file as backup
        val sourceFile = File(project.projectPath)
        if (sourceFile.exists()) {
            sourceFile.copyTo(backupFile, overwrite = true)
        }

        val backup = ProjectBackup(
            backupId = backupId,
            projectId = projectId,
            projectName = project.projectName,
            backupPath = backupFile.absolutePath,
            createdAt = Instant.now(),
            packageVersion = project.packageVersion,
            fileSize = backupFile.length()
        )

        // Persist backup metadata
        saveBackupMetadata(backup)
        Log.i(TAG, "Backup created: ${backupFile.absolutePath}")
        backup
    }

    suspend fun restoreBackup(backupId: String): Project = withContext(Dispatchers.IO) {
        val backup = loadBackupMetadata(backupId)
            ?: throw IllegalStateException("Backup not found: $backupId")

        val backupFile = File(backup.backupPath)
        if (!backupFile.exists()) {
            throw IllegalStateException("Backup file not found: ${backup.backupPath}")
        }

        // Close current project
        closeProject()

        // Open the backup as a project
        openProject(backup.backupPath)
    }

    suspend fun listBackups(projectId: String? = null): List<ProjectBackup> = withContext(Dispatchers.IO) {
        val backups = loadAllBackupMetadata()
        projectId?.let { pid -> backups.filter { it.projectId == pid } } ?: backups
    }

    suspend fun deleteBackup(backupId: String) = withContext(Dispatchers.IO) {
        val backup = loadBackupMetadata(backupId) ?: return@withContext
        val backupFile = File(backup.backupPath)
        if (backupFile.exists()) backupFile.delete()
        removeBackupMetadata(backupId)
    }

    private fun saveBackupMetadata(backup: ProjectBackup) {
        val prefs = context.getSharedPreferences("alcedo_backups", Context.MODE_PRIVATE)
        val json = kotlinx.serialization.json.Json.encodeToString(
            ProjectBackup.serializer(), backup
        )
        prefs.edit().putString("backup_${backup.backupId}", json).apply()
        val index = prefs.getStringSet("backup_index", emptySet())?.toMutableSet() ?: mutableSetOf()
        index.add(backup.backupId)
        prefs.edit().putStringSet("backup_index", index).apply()
    }

    private fun loadBackupMetadata(backupId: String): ProjectBackup? {
        val prefs = context.getSharedPreferences("alcedo_backups", Context.MODE_PRIVATE)
        val json = prefs.getString("backup_$backupId", null) ?: return null
        return try {
            kotlinx.serialization.json.Json.decodeFromString(ProjectBackup.serializer(), json)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadAllBackupMetadata(): List<ProjectBackup> {
        val prefs = context.getSharedPreferences("alcedo_backups", Context.MODE_PRIVATE)
        val index = prefs.getStringSet("backup_index", emptySet()) ?: return emptyList()
        return index.mapNotNull { loadBackupMetadata(it) }
            .sortedByDescending { it.createdAt }
    }

    private fun removeBackupMetadata(backupId: String) {
        val prefs = context.getSharedPreferences("alcedo_backups", Context.MODE_PRIVATE)
        prefs.edit().remove("backup_$backupId").apply()
        val index = prefs.getStringSet("backup_index", emptySet())?.toMutableSet() ?: return
        index.remove(backupId)
        prefs.edit().putStringSet("backup_index", index).apply()
    }

    // ── Current Project Accessors ──

    fun getCurrentProject(): Project? {
        val id = currentProjectId ?: return null
        return openProjects[id]
    }

    fun getCurrentProjectId(): String? = currentProjectId

    fun isProjectOpen(): Boolean = currentProjectId != null

    // ── Cleanup ──

    fun shutdown() {
        scope.cancel()
    }
}