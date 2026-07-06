package com.alcedo.studio.storage

import android.net.Uri

data class PhotoPickerResult(
    val uris: List<Uri>,
    val isPhotoPicker: Boolean
)

object PhotoPickerHelper {
    // PickMultipleVisualMedia from androidx.activity handles backporting
    // automatically: it uses the system Photo Picker on Android 13+,
    // the Google Play Services backport on Android 11-12, and falls back
    // to ACTION_GET_CONTENT on older versions. So it is ALWAYS available.
    fun isAvailable(): Boolean = true
}
