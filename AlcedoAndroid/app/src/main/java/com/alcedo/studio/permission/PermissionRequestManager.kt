package com.alcedo.studio.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
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
                    photoPickerLauncher?.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            .build()
                    )
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
    /**
     * 请求媒体读取权限，支持 Android 14+ 的部分访问流程。
     * - 在 Android 14+ 上，请求列表包含 READ_MEDIA_VISUAL_USER_SELECTED，
     *   系统会根据用户选择授予完整权限或仅授予部分权限。
     * - 如果用户之前仅授予了部分权限，再次调用此方法可以重新请求完整权限。
     */
    fun requestMediaAccess() {
        val permissions = PermissionHelper.getReadMediaPermissions()
        if (!PermissionHelper.hasReadMediaAccess(context)) {
            requestPermissions(permissions)
        }
    }

    /**
     * 请求完整媒体访问权限（Android 14+ 专用）。
     * 当 app 目前仅有部分访问权限（READ_MEDIA_VISUAL_USER_SELECTED）时，
     * 调用此方法重新请求 READ_MEDIA_IMAGES + READ_MEDIA_VIDEO，
     * 系统将再次弹出权限对话框让用户选择"允许全部"。
     * 在低于 Android 14 的设备上，行为与 [requestMediaAccess] 相同。
     */
    fun requestMediaAccessWithPartialSupport() {
        if (PermissionHelper.isLimitedAccess(context)) {
            // 当前为受限访问，重新请求完整权限以引导用户选择"允许全部"
            val fullPermissions = listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            requestPermissions(fullPermissions)
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

    /**
     * 检查当前是否仅有部分媒体访问权限。
     * 在 Android 14+ 上，当用户选择了"选择照片"而非"允许全部"时返回 true。
     */
    fun isLimitedMediaAccess(): Boolean = PermissionHelper.isLimitedAccess(context)

    /**
     * 检查当前是否有部分媒体访问权限。
     * 与 [isLimitedMediaAccess] 语义一致。
     */
    fun hasPartialMediaAccess(): Boolean = PermissionHelper.hasPartialMediaAccess(context)
}
