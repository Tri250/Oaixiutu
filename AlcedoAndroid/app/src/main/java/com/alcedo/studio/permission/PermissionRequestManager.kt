package com.alcedo.studio.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.Manifest
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

// ================================================================
// Composable function to request permissions
// ================================================================

@Composable
fun rememberPermissionState(
    onResult: (Map<String, Boolean>) -> Unit = {}
): PermissionStateHolder {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val launcher = activity?.let {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            onResult(results)
        }
    }

    val safLauncher = activity?.let {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            // Handle SAF directory selection
            if (uri != null) {
                val contentResolver = context.contentResolver
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            }
        }
    }

    val photoPickerLauncher = activity?.let {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(50)
        ) { uris: List<Uri> ->
            // Handle photo picker results
        }
    }

    return remember {
        PermissionStateHolder(
            requestPermissions = { permissions ->
                launcher?.launch(permissions.toTypedArray())
            },
            requestSafDirectory = {
                safLauncher?.launch(Uri.EMPTY)
            },
            requestPhotoPicker = { mimeType ->
                if (PermissionHelper.supportsPhotoPicker()) {
                    photoPickerLauncher?.launch(mimeType ?: "image/*")
                }
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

    /**
     * Request full photo access on Android 14+. If the user previously chose
     * "Select photos" (partial access), re-requesting READ_MEDIA_IMAGES will
     * show the system dialog allowing them to upgrade to full access.
     */
    fun requestFullPhotoAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!PermissionHelper.hasFullPhotoAccess(context)) {
                requestPermissions(listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                ))
            }
        } else {
            requestMediaAccess()
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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun hasPartialPhotoAccess(): Boolean = PermissionHelper.hasPartialPhotoAccess(context)

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun hasFullPhotoAccess(): Boolean = PermissionHelper.hasFullPhotoAccess(context)
}
