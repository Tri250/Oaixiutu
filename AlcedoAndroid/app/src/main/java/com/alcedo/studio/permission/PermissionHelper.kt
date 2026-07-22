package com.alcedo.studio.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ================================================================
// Permission types for this app
// ================================================================

enum class PermissionType(val description: String) {
    READ_MEDIA("Read photos and videos"),
    WRITE_MEDIA("Save edited photos"),
    CAMERA("Take photos"),
    NOTIFICATION("Show notifications")
}

data class PermissionState(
    val granted: Set<String> = emptySet(),
    val denied: Set<String> = emptySet(),
    val permanentlyDenied: Set<String> = emptySet(),
    val shouldShowRationale: Set<String> = emptySet()
)

// ================================================================
// Permission Helper
// ================================================================

object PermissionHelper {

    // Get the correct permissions based on API level
    fun getReadMediaPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: Include READ_MEDIA_VISUAL_USER_SELECTED for partial access support.
            // When requesting together, the system handles the "Select photos" flow:
            // - User grants full access → READ_MEDIA_IMAGES + READ_MEDIA_VIDEO granted
            // - User selects "Select photos" → READ_MEDIA_VISUAL_USER_SELECTED granted only
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13: Use granular media permissions
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12: READ_EXTERNAL_STORAGE still works for reading
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 10 and below
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun getWritePermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use MediaStore or SAF for writing, no permission needed
            null
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
    }

    fun needsManageExternalStorage(): Boolean {
        // Only request MANAGE_EXTERNAL_STORAGE as a last resort for advanced features
        // like accessing RAW files in arbitrary directories
        return false // Default: don't request. Use SAF/MediaStore instead.
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllPermissions(context: Context, permissions: List<String>): Boolean {
        return permissions.all { hasPermission(context, it) }
    }

    /**
     * 检查是否有媒体读取访问权限（完整或部分）。
     * - 完整访问：READ_MEDIA_IMAGES + READ_MEDIA_VIDEO 均已授予
     * - 部分访问（Android 14+）：仅 READ_MEDIA_VISUAL_USER_SELECTED 已授予
     * 两种情况均返回 true。调用方可通过 [isLimitedAccess] 区分是否为受限访问。
     */
    fun hasReadMediaAccess(context: Context): Boolean {
        if (hasAllPermissions(context, getFullReadMediaPermissions())) {
            return true
        }
        // Android 14+: 用户选择了"选择照片"，仅授予了 READ_MEDIA_VISUAL_USER_SELECTED
        return hasPartialMediaAccess(context)
    }

    /**
     * 返回完整媒体访问所需的权限列表（不含 READ_MEDIA_VISUAL_USER_SELECTED）。
     */
    private fun getFullReadMediaPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * 检查是否仅有部分媒体访问权限。
     * 在 Android 14+ 上，当 READ_MEDIA_VISUAL_USER_SELECTED 已授予，
     * 但 READ_MEDIA_IMAGES 未授予时，返回 true。
     * 低于 Android 14 的设备始终返回 false。
     */
    fun hasPartialMediaAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
                    !hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
        }
        return false
    }

    /**
     * 检查是否处于受限访问状态（用户在权限对话框中选择了"选择照片"）。
     * 即 READ_MEDIA_VISUAL_USER_SELECTED 已授予，但 READ_MEDIA_IMAGES 未授予。
     * 这与 [hasPartialMediaAccess] 语义一致，提供更具描述性的命名。
     */
    fun isLimitedAccess(context: Context): Boolean {
        return hasPartialMediaAccess(context)
    }

    fun hasWriteAccess(context: Context): Boolean {
        val writePerm = getWritePermission()
        return if (writePerm != null) {
            hasPermission(context, writePerm)
        } else {
            true // On Android 10+, we use app-specific or MediaStore directories
        }
    }

    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    /**
     * 检查是否应该为读取媒体权限显示解释。
     * 仅当当前权限未授予，且至少一个未授予权限应显示 rationale 时返回 true。
     * 若所有未授予权限都不应显示 rationale，则视为已被永久拒绝。
     */
    fun shouldShowRationale(context: Context): Boolean {
        val activity = context as? Activity ?: return false
        val permissions = getReadMediaPermissions()
        return permissions.any {
            !hasPermission(activity, it) && activity.shouldShowRequestPermissionRationale(it)
        }
    }

    fun isPermanentlyDenied(activity: Activity, permission: String, wasRequested: Boolean): Boolean {
        // If the permission was requested before but shouldShowRationale returns false,
        // and the permission is not granted, it means "Don't ask again" was selected
        return wasRequested && !hasPermission(activity, permission) &&
               !activity.shouldShowRequestPermissionRationale(permission)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // Check if we can use the Android 13+ Photo Picker
    fun supportsPhotoPicker(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    // Check if we need to use SAF for directory access
    fun needsSafForDirectoryAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}

// ================================================================
// Compose Permission State
// ================================================================

interface PermissionStateHandle {
    fun requestMediaAccess()
}

@Composable
fun rememberPermissionState(
    onResult: (Map<String, Boolean>) -> Unit
): PermissionStateHandle {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onResult(results)
    }

    return remember {
        object : PermissionStateHandle {
            override fun requestMediaAccess() {
                val permissions = PermissionHelper.getReadMediaPermissions()
                launcher.launch(permissions.toTypedArray())
            }
        }
    }
}
