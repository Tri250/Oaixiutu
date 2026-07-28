package com.alcedo.studio.ui.editor

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.service.ExifEditorService
import com.alcedo.studio.domain.service.HistoryMgmtService
import com.alcedo.studio.domain.service.MaskService
import com.alcedo.studio.domain.service.PipelineService
import com.alcedo.studio.domain.service.PresetService

/**
 * Factory for creating an [EditorViewModel] bound to a specific [imageId].
 *
 * In production the editor uses Hilt (`hiltViewModel()`), which injects the
 * services and seeds [EditorViewModel.imageId] from the nav arguments via
 * [SavedStateHandle]. This factory exists for non-Hilt entry points (previews,
 * instrumentation tests, embedded editor fragments) where the dependencies are
 * constructed manually and the image id must be supplied explicitly.
 *
 * Usage:
 * ```
 * val factory = EditorViewModelFactory(owner, imageId, services...)
 * val vm = ViewModelProvider(owner, factory)[EditorViewModel::class.java]
 * ```
 */
class EditorViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val imageId: String,
    private val pipelineService: PipelineService,
    private val historyService: HistoryMgmtService,
    private val presetService: PresetService,
    private val maskService: MaskService,
    private val exifService: ExifEditorService,
    private val imageRepository: ImageRepository,
) : AbstractSavedStateViewModelFactory(owner, null) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle,
    ): T {
        handle[EditorViewModel.KEY_IMAGE_ID] = imageId
        @Suppress("UNCHECKED_CAST")
        return EditorViewModel(
            pipelineService = pipelineService,
            historyService = historyService,
            presetService = presetService,
            maskService = maskService,
            exifService = exifService,
            imageRepository = imageRepository,
            savedStateHandle = handle,
        ) as T
    }
}
