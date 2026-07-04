package com.alcedo.studio.storage

import android.net.Uri

data class PhotoPickerResult(
    val uris: List<Uri>,
    val isPhotoPicker: Boolean
)

object PhotoPickerHelper {
    // Photo Picker is available on Android 13+ (API 33+)
    // and via Google Play Services on Android 11+ (API 30+)
    fun isAvailable(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    }

    // For devices without Photo Picker, fall back to gallery intent
    fun getGalleryIntent(): android.content.Intent {
        return if (isAvailable()) {
            // Use system photo picker
            android.provider.MediaStore.ACTION_PICK_IMAGES.let { action ->
                android.content.Intent(action).apply {
                    type = "image/*"
                    // Allow multiple selection
                    putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, 50)
                }
            }
        } else {
            // Fallback to ACTION_GET_CONTENT
            android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("image/*"))
            }
        }
    }
}
