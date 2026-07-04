package com.alcedo.studio.permission

import android.content.Context
import androidx.compose.runtime.*

@Composable
fun rememberPermissionState(
    onResult: (Map<String, Boolean>) -> Unit = {}
): PermissionStateHolder {
    val context = androidx.compose.ui.platform.LocalContext.current

    return remember {
        PermissionStateHolder(
            requestPermissions = { permissions ->
                // No-op stub - actual permission request requires Activity
            },
            requestSafDirectory = {
                // No-op stub
            },
            requestPhotoPicker = { _ ->
                // No-op stub
            },
            context = context
        )
    }
}

class PermissionStateHolder(
    val requestPermissions: (List<String>) -> Unit,
    val requestSafDirectory: () -> Unit,
    val requestPhotoPicker: (String?) -> Unit,
    private val context: Context
) {
    fun requestMediaAccess() {
        val permissions = PermissionHelper.getReadMediaPermissions()
        if (!PermissionHelper.hasAllPermissions(context, permissions)) {
            requestPermissions(permissions)
        }
    }

    fun requestWriteAccess() {
        val writePerm = PermissionHelper.getWritePermission()
        if (writePerm != null && !PermissionHelper.hasPermission(context, writePerm)) {
            requestPermissions(listOf(writePerm))
        }
    }

    fun openPhotoPicker(mimeType: String? = null) {
        requestPhotoPicker(mimeType)
    }

    fun openSafDirectoryPicker() {
        requestSafDirectory()
    }

    fun hasMediaAccess(): Boolean = PermissionHelper.hasReadMediaAccess(context)
    fun hasWriteAccess(): Boolean = PermissionHelper.hasWriteAccess(context)
}
