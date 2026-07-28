package com.alcedo.studio.ui.album

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.AiRating
import com.alcedo.studio.data.model.BackgroundTaskInfo
import com.alcedo.studio.data.model.BackgroundTaskType
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.FilterCombo
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.SortDescriptor
import com.alcedo.studio.data.model.SortField
import com.alcedo.studio.data.model.SleeveConstants
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.service.AiRatingService
import com.alcedo.studio.domain.service.AlbumBrowseService
import com.alcedo.studio.domain.service.BackgroundTaskService
import com.alcedo.studio.domain.service.ImportService
import com.alcedo.studio.domain.service.SearchService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Album screen ViewModel. Owns the grid's reactive data: the current folder,
 * filter and sort drive a [flatMapLatest] that switches between the live folder
 * observation (when no filter is active) and a one-shot filtered [query]. Also
 * exposes import, AI culling, semantic search and per-image metadata actions.
 *
 * Long-running work (import, culling) is registered with [BackgroundTaskService]
 * so the [BackgroundTaskBar] overlay reflects progress and ETA.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val albumService: AlbumBrowseService,
    private val importService: ImportService,
    private val imageRepository: ImageRepository,
    private val searchService: SearchService,
    private val aiRatingService: AiRatingService,
    private val taskService: BackgroundTaskService,
) : ViewModel() {

    data class AlbumUiState(
        val folderPath: String? = null,
        val filter: FilterCombo = FilterCombo(),
        val sort: SortDescriptor = SortDescriptor(SortField.DATE_CAPTURED, ascending = false),
        val images: List<ImageItem> = emptyList(),
        val selection: Set<String> = emptySet(),
        val stats: AlbumBrowseService.AlbumStats? = null,
        val cameras: List<String> = emptyList(),
        val lenses: List<String> = emptyList(),
        val searchQuery: String = "",
        val searchResults: List<SearchService.SearchResult> = emptyList(),
        val isSearching: Boolean = false,
        val isImporting: Boolean = false,
        val isCulling: Boolean = false,
        val topRated: List<AiRating> = emptyList(),
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    /** Reactive background task list for the task bar overlay. */
    val backgroundTasks: StateFlow<List<BackgroundTaskInfo>> =
        taskService.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Reactive grid drivers -------------------------------------------

    private val folderPath = MutableStateFlow<String?>(null)
    private val filter = MutableStateFlow(FilterCombo())
    private val sort = MutableStateFlow(albumService.defaultSort())

    init {
        // Switch the observed stream whenever folder/filter/sort changes. With no
        // filter we reuse the live repository observation (folder-scoped); with a
        // filter we run a paged query and emit its result once.
        viewModelScope.launch {
            combine(folderPath, filter, sort) { folder, f, s -> Triple(folder, f, s) }
                .flatMapLatest { (folder, f, s) ->
                    if (f.isEmpty) {
                        folder?.let { albumService.observeFolder(it) } ?: albumService.observeAll()
                    } else {
                        flow { emit(albumService.query(f, s, page = 0, pageSize = PAGE_SIZE)) }
                    }
                }
                .collect { images ->
                    _uiState.update { it.copy(images = images, error = null) }
                }
        }

        // Mirror import progress into UI state.
        viewModelScope.launch {
            importService.progress.collect { p ->
                _uiState.update {
                    it.copy(isImporting = p.completed < p.total && p.total > 0)
                }
            }
        }

        refreshStats()
    }

    // ---- Navigation / filter / sort --------------------------------------

    fun setFolder(path: String?) {
        folderPath.value = path
        _uiState.update { it.copy(folderPath = path, selection = emptySet()) }
    }

    fun setFilter(newFilter: FilterCombo) {
        filter.value = newFilter
        _uiState.update { it.copy(filter = newFilter, selection = emptySet()) }
    }

    fun setSort(newSort: SortDescriptor) {
        sort.value = newSort
        _uiState.update { it.copy(sort = newSort) }
    }

    fun clearFilter() = setFilter(FilterCombo())

    // ---- Selection --------------------------------------------------------

    fun toggleSelection(id: String) {
        _uiState.update {
            val next = if (id in it.selection) it.selection - id else it.selection + id
            it.copy(selection = next)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selection = it.images.map(ImageItem::id).toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selection = emptySet()) }
    }

    // ---- Per-image metadata actions --------------------------------------

    fun setRating(id: String, rating: Int) = viewModelScope.launch {
        runCatching { imageRepository.setRating(id, rating.coerceIn(0, 5)) }
            .onFailure { e -> emitError(e) }
    }

    fun setFlag(id: String, flag: ImageFlag) = viewModelScope.launch {
        runCatching { imageRepository.setFlag(id, flag) }
            .onFailure { e -> emitError(e) }
    }

    fun setColorLabel(id: String, label: ColorLabel) = viewModelScope.launch {
        runCatching { imageRepository.setColorLabel(id, label) }
            .onFailure { e -> emitError(e) }
    }

    fun setHidden(id: String, hidden: Boolean) = viewModelScope.launch {
        runCatching { imageRepository.setHidden(id, hidden) }
            .onFailure { e -> emitError(e) }
    }

    fun delete(id: String) = viewModelScope.launch {
        runCatching { imageRepository.delete(id) }
            .onSuccess { clearSelection() }
            .onFailure { e -> emitError(e) }
    }

    /** Apply a metadata action to every selected image. */
    fun applyRatingToSelection(rating: Int) = applyToSelection { setRating(it, rating) }
    fun applyFlagToSelection(flag: ImageFlag) = applyToSelection { setFlag(it, flag) }
    fun applyColorLabelToSelection(label: ColorLabel) = applyToSelection { setColorLabel(it, label) }

    private fun applyToSelection(action: suspend (String) -> Unit) = viewModelScope.launch {
        _uiState.value.selection.forEach { id -> runCatching { action(id) } }
        refreshStats()
    }

    // ---- Import -----------------------------------------------------------

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val taskId = taskService.start(BackgroundTaskType.IMPORT, "Importing ${uris.size} images", uris.size)
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val dest = folderPath.value ?: SleeveConstants.DEFAULT_IMPORT_FOLDER
            runCatching { importService.import(uris, dest) }
                .onSuccess { imported ->
                    taskService.update(taskId, uris.size, uris.size)
                    taskService.complete(taskId)
                    refreshStats()
                }
                .onFailure { e ->
                    taskService.complete(taskId, e.message)
                    emitError(e)
                }
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    // ---- AI culling -------------------------------------------------------

    /** Rate the current selection via the LLM/heuristic culling pipeline. */
    fun cullSelection() {
        val state = _uiState.value
        if (state.selection.isEmpty() || state.isCulling) return
        val ids = state.selection.toList()
        val total = ids.size
        val taskId = taskService.start(BackgroundTaskType.AI_RATING, "AI culling $total images", total)
        _uiState.update { it.copy(isCulling = true) }
        viewModelScope.launch {
            val items = mutableListOf<Pair<Uri, String>>()
            val metadata = mutableMapOf<String, Map<String, String>>()
            ids.forEach { id ->
                val img = imageRepository.getImage(id) ?: return@forEach
                items += Uri.parse(img.originalUri) to id
                metadata[id] = buildMetadata(img)
            }
            runCatching {
                aiRatingService.cullBatch(items, metadata) { completed, t ->
                    taskService.update(taskId, completed, t)
                }
            }.onSuccess { ratings ->
                taskService.complete(taskId)
                _uiState.update { it.copy(topRated = ratings.sortedByDescending { r -> r.overallScore }) }
                refreshStats()
            }.onFailure { e ->
                taskService.complete(taskId, e.message)
                emitError(e)
            }
            _uiState.update { it.copy(isCulling = false) }
        }
    }

    /** Load the globally top-rated images for the "Best of" smart collection. */
    fun loadTopRated(limit: Int = 50) = viewModelScope.launch {
        runCatching { aiRatingService.topRated(limit) }
            .onSuccess { ratings -> _uiState.update { it.copy(topRated = ratings) } }
            .onFailure { e -> emitError(e) }
    }

    // ---- Semantic search --------------------------------------------------

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            runCatching { searchService.search(query, SEARCH_LIMIT) }
                .onSuccess { results -> _uiState.update { it.copy(searchResults = results, isSearching = false) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isSearching = false) }
                    emitError(e)
                }
        }
    }

    fun clearSearch() = search("")

    // ---- Stats -----------------------------------------------------------

    fun refreshStats() = viewModelScope.launch {
        runCatching {
            val stats = albumService.stats()
            val cameras = albumService.cameras()
            val lenses = albumService.lenses()
            _uiState.update { it.copy(stats = stats, cameras = cameras, lenses = lenses) }
        }.onFailure { e -> emitError(e) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun emitError(e: Throwable) {
        _uiState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
    }

    private fun buildMetadata(img: ImageItem): Map<String, String> = buildMap {
        img.iso?.let { put("iso", it.toString()) }
        img.aperture?.let { put("aperture", it.toString()) }
        img.focalLength?.let { put("focalLength", it.toString()) }
        img.cameraModel?.let { put("cameraModel", it) }
        img.lensModel?.let { put("lensModel", it) }
        img.shutterSpeed?.let { put("shutterSpeed", it) }
    }

    override fun onCleared() {
        super.onCleared()
        // Selection/import/culling state is transient; nothing to release here.
    }

    companion object {
        private const val PAGE_SIZE = 500
        private const val SEARCH_LIMIT = 100
    }
}
