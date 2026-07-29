package com.alcedo.studio.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Permission management. Centralises the runtime permissions Alcedo needs
 * (media access on API 33+, notifications for foreground tasks) and provides a
 * Compose-friendly launcher that requests them on demand, including rationale
 * handling and a redirect to the system settings page when the user has chosen
 * "Don't ask again".
 */
object PermissionHelper {

    /** Permissions required for reading media (version-aware). */
    fun requiredMediaPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Notification permission (API 33+). */
    fun notificationPermission(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyList()

    /** Write permission for exporting images to shared storage (API 29 and below). */
    fun writeStoragePermission(): List<String> =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else emptyList()

    /** All runtime permissions the app needs at first launch. */
    fun allRequired(): List<String> = requiredMediaPermissions() + notificationPermission()

    /** All permissions needed for export functionality. */
    fun exportPermissions(): List<String> = writeStoragePermission()

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

    /** The subset of [permissions] that are not yet granted. */
    fun deniedPermissions(context: Context, permissions: List<String>): List<String> =
        permissions.filterNot { isGranted(context, it) }

    /** The subset of [permissions] that are granted. */
    fun grantedPermissions(context: Context, permissions: List<String>): List<String> =
        permissions.filter { isGranted(context, it) }

    /**
     * True when the system would show a rationale UI for [permission] (i.e. the
     * user previously denied it without checking "Don't ask again"). Requires
     * an [Activity]; pass `null` to short-circuit to false.
     */
    fun shouldShowRationale(activity: Activity?, permission: String): Boolean =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    /** True when rationale should be shown for ANY of [permissions]. */
    fun shouldShowRationaleForAny(activity: Activity?, permissions: List<String>): Boolean =
        permissions.any { shouldShowRationale(activity, it) }

    /**
     * True when [permission] is permanently denied: not granted AND the system
     * will no longer show the permission dialog (rationale returns false after a
     * prior request). A granted permission is never "denied" even though its
     * rationale is false, and a null activity can't be evaluated, so both return
     * false here. NOTE: on the very first launch (before any request) rationale
     * is also false, so only treat this as truly permanent after a
     * [rememberPermissionLauncher] request has returned at least once.
     */
    fun isPermanentlyDenied(activity: Activity?, permission: String): Boolean {
        if (activity == null) return false
        // A granted permission is not denied, regardless of the rationale flag.
        if (isGranted(activity, permission)) return false
        return !shouldShowRationale(activity, permission)
    }

    /** True when ANY of the [permissions] (all ungranted) is permanently denied. */
    fun anyPermanentlyDenied(activity: Activity?, context: Context, permissions: List<String>): Boolean {
        val denied = deniedPermissions(context, permissions)
        return denied.any { isPermanentlyDenied(activity, it) }
    }

    /**
     * Open the system "App info" screen so the user can grant permissions they
     * previously denied with "Don't ask again". Safe to call from any context.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}

/**
 * Compose helper that requests [permissions] (in a single batch) and reports
 * the result via [onResult]. Returns a launcher that can be triggered from a
 * button.
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
 * re-checking on recomposition. Triggers a request via the returned launcher.
 * Carries [PermissionState.denied] and [PermissionState.shouldShowRationale]
 * so the UI can show a rationale dialog and redirect to settings when the user
 * selected "Don't ask again".
 */
@Composable
fun rememberPermissionState(
    permissions: List<String>,
): PermissionState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    var granted by remember {
        mutableStateOf(PermissionHelper.areAllGranted(context, permissions))
    }
    var requestedOnce by remember { mutableStateOf(false) }
    val launcher = rememberPermissionLauncher(permissions) { result ->
        requestedOnce = true
        granted = result.values.all { it }
    }
    val denied = PermissionHelper.deniedPermissions(context, permissions)
    val showRationale = PermissionHelper.shouldShowRationaleForAny(activity, denied)
    // After at least one request, "denied + no rationale" means the user picked
    // "Don't ask again" — the only way forward is the system settings page.
    val permanentlyDenied = requestedOnce && denied.isNotEmpty() && !showRationale
    return remember(granted, requestedOnce, showRationale, permanentlyDenied) {
        PermissionState(
            allGranted = granted,
            denied = denied,
            shouldShowRationale = showRationale,
            permanentlyDenied = permanentlyDenied,
            launcher = launcher,
        )
    }
}

data class PermissionState(
    val allGranted: Boolean,
    val denied: List<String> = emptyList(),
    val shouldShowRationale: Boolean = false,
    val permanentlyDenied: Boolean = false,
    val launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
)
