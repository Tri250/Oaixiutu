package com.alcedo.studio.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Permission management. Centralises the runtime permissions Alcedo needs
 * (media access on API 33+, notifications for foreground tasks) and provides a
 * Compose-friendly launcher that requests them on demand.
 */
object PermissionHelper {

    /** Permissions required for reading media (version-aware). */
    fun requiredMediaPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Notification permission (API 33+). */
    fun notificationPermission(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyList()

    /** All runtime permissions the app needs at first launch. */
    fun allRequired(): List<String> = requiredMediaPermissions() + notificationPermission()

    /** True when [permission] is granted. */
    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** True when all of [permissions] are granted. */
    fun areAllGranted(context: Context, permissions: List<String>): Boolean =
        permissions.all { isGranted(context, it) }

    /** True when media-access permissions are satisfied. */
    fun hasMediaAccess(context: Context): Boolean =
        areAllGranted(context, requiredMediaPermissions())

    /** True when notification permission is granted (or not required). */
    fun hasNotificationAccess(context: Context): Boolean =
        notificationPermission().isEmpty() || areAllGranted(context, notificationPermission())
}

/**
 * Compose helper that requests [permissions] and reports the result via [onResult].
 * Returns a launcher that can be triggered from a button.
 */
@Composable
fun rememberPermissionLauncher(
    permissions: List<String>,
    onResult: (Map<String, Boolean>) -> Unit,
): androidx.activity.result.ActivityResultLauncher<Array<String>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> onResult(result) }
}

/**
 * Compose state that tracks whether all required permissions are granted,
 * re-checking on resume. Triggers a request via the returned launcher.
 */
@Composable
fun rememberPermissionState(
    permissions: List<String>,
): PermissionState {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(PermissionHelper.areAllGranted(context, permissions)) }
    val launcher = rememberPermissionLauncher(permissions) { result ->
        granted = result.values.all { it }
    }
    return remember(granted) { PermissionState(granted, launcher) }
}

data class PermissionState(
    val allGranted: Boolean,
    val launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
)
