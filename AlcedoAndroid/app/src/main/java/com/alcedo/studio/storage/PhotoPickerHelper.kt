package com.alcedo.studio.storage

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Photo picker helper. Wraps the AndroidX Photo Picker
 * ([ActivityResultContracts.PickMultipleVisualMedia]) for selecting images to
 * import, with a Compose-friendly launcher. Falls back to GET_CONTENT on
 * devices where the system photo picker is unavailable.
 */
object PhotoPickerHelper {

    /** True when the system photo picker is available on this device. */
    fun isPhotoPickerAvailable(context: Context): Boolean =
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)

    /** Launch hint for image-only picker requests. */
    fun imageOnly(): PickVisualMediaRequest = PickVisualMediaRequest(
        ActivityResultContracts.PickVisualMedia.ImageOnly,
    )
}

/**
 * Compose launcher that requests multiple images and invokes [onResult] with
 * the selected URIs. Use inside a composable; the launcher is remembered.
 */
@Composable
fun rememberImagePicker(
    maxItems: Int = 20,
    onResult: (List<Uri>) -> Unit,
): androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest> {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems),
    ) { uris -> onResult(uris) }
    return launcher
}
