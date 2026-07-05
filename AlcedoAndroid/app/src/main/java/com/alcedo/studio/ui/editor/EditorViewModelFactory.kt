package com.alcedo.studio.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alcedo.studio.viewmodel.EditorViewModel

/**
 * Factory for EditorViewModel.
 *
 * EditorViewModel resolves its repositories lazily through AppModule, so the
 * only parameter the factory needs to thread through is the image id.
 */
class EditorViewModelFactory(
    private val imageId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            return EditorViewModel(imageId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
