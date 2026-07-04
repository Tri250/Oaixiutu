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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Use granular media permissions
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

    fun hasReadMediaAccess(context: Context): Boolean {
        return hasAllPermissions(context, getReadMediaPermissions())
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
