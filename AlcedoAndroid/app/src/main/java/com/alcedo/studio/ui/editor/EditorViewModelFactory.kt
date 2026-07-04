package com.alcedo.studio.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alcedo.studio.domain.service.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.viewmodel.EditorViewModel

class EditorViewModelFactory(
    private val imageId: String,
    private val imageRepository: ImageRepository = AppModule.imageRepository,
    private val editHistoryRepository: EditHistoryRepository = AppModule.editHistoryRepository,
    private val pipelineService: PipelineService = AppModule.pipelineService,
    private val exportService: ExportService = AppModule.exportService,
    private val thumbnailService: ThumbnailService = AppModule.thumbnailService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(imageId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}