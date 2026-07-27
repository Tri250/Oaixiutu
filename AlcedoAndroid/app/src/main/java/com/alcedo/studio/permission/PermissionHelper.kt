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
import android.util.Log
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

private const val TAG = "PermissionHelper"

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

data class PermissionAuditResult(
    val timestamp: Long,
    val grantedPermissions: List<String>,
    val deniedPermissions: List<String>,
    val permanentlyDeniedPermissions: List<String>,
    val notes: List<String>
)

// ================================================================
// Permission Helper
// ================================================================

object PermissionHelper {

    // Get the correct permissions based on API level
    fun getReadMediaPermissions(): List<String> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        }.getOrElse { e ->
            Log.e(TAG, "getReadMediaPermissions failed", e)
            emptyList()
        }
    }

    fun getWritePermission(): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                null
            } else {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }.getOrElse { e ->
            Log.e(TAG, "getWritePermission failed", e)
            null
        }
    }

    fun needsManageExternalStorage(): Boolean {
        return runCatching {
            false
        }.getOrElse { e ->
            Log.e(TAG, "needsManageExternalStorage failed", e)
            false
        }
    }

    /**
     * checkSelfPermission 的安全包装，捕获系统调用异常
     * 某些 ROM 或特殊情况下 ContextCompat.checkSelfPermission 可能抛出异常
     */
    fun checkSelfPermissionSafe(context: Context, permission: String): Int {
        return runCatching {
            ContextCompat.checkSelfPermission(context, permission)
        }.getOrElse { e ->
            Log.w(TAG, "checkSelfPermissionSafe failed for $permission, assuming denied", e)
            PackageManager.PERMISSION_DENIED
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return runCatching {
            checkSelfPermissionSafe(context, permission) == PackageManager.PERMISSION_GRANTED
        }.getOrElse { e ->
            Log.e(TAG, "hasPermission failed for $permission", e)
            false
        }
    }

    fun hasAllPermissions(context: Context, permissions: List<String>): Boolean {
        return runCatching {
            permissions.all { hasPermission(context, it) }
        }.getOrElse { e ->
            Log.e(TAG, "hasAllPermissions failed", e)
            false
        }
    }

    fun hasReadMediaAccess(context: Context): Boolean {
        return runCatching {
            if (hasAllPermissions(context, getFullReadMediaPermissions())) {
                return@runCatching true
            }
            hasPartialMediaAccess(context)
        }.getOrElse { e ->
            Log.e(TAG, "hasReadMediaAccess failed", e)
            false
        }
    }

    private fun getFullReadMediaPermissions(): List<String> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        }.getOrElse { e ->
            Log.e(TAG, "getFullReadMediaPermissions failed", e)
            emptyList()
        }
    }

    fun hasPartialMediaAccess(context: Context): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return@runCatching hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
                        !hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
            }
            false
        }.getOrElse { e ->
            Log.e(TAG, "hasPartialMediaAccess failed", e)
            false
        }
    }

    fun isLimitedAccess(context: Context): Boolean {
        return runCatching {
            hasPartialMediaAccess(context)
        }.getOrElse { e ->
            Log.e(TAG, "isLimitedAccess failed", e)
            false
        }
    }

    fun hasWriteAccess(context: Context): Boolean {
        return runCatching {
            val writePerm = getWritePermission()
            if (writePerm != null) {
                hasPermission(context, writePerm)
            } else {
                true
            }
        }.getOrElse { e ->
            Log.e(TAG, "hasWriteAccess failed", e)
            false
        }
    }

    /**
     * shouldShowRequestPermissionRationale 的安全包装
     * 某些 Android 版本或 ROM 上此方法可能抛出异常
     */
    fun shouldShowRequestPermissionRationaleSafe(activity: Activity, permission: String): Boolean {
        return runCatching {
            activity.shouldShowRequestPermissionRationale(permission)
        }.getOrElse { e ->
            Log.w(TAG, "shouldShowRequestPermissionRationaleSafe failed for $permission", e)
            false
        }
    }

    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return runCatching {
            shouldShowRequestPermissionRationaleSafe(activity, permission)
        }.getOrElse { e ->
            Log.e(TAG, "shouldShowRationale(Activity) failed", e)
            false
        }
    }

    fun shouldShowRationale(context: Context): Boolean {
        return runCatching {
            val activity = context as? Activity ?: return@runCatching false
            val permissions = getReadMediaPermissions()
            permissions.any {
                !hasPermission(activity, it) && shouldShowRequestPermissionRationaleSafe(activity, it)
            }
        }.getOrElse { e ->
            Log.e(TAG, "shouldShowRationale(Context) failed", e)
            false
        }
    }

    fun isPermanentlyDenied(activity: Activity, permission: String, wasRequested: Boolean): Boolean {
        return runCatching {
            wasRequested && !hasPermission(activity, permission) &&
                    !shouldShowRequestPermissionRationaleSafe(activity, permission)
        }.getOrElse { e ->
            Log.e(TAG, "isPermanentlyDenied failed for $permission", e)
            false
        }
    }

    /**
     * 验证 URI 安全性，防止 Intent URI 注入攻击
     * 确保 scheme 为 "package"，并且 ssp 只包含安全字符
     */
    private fun isSafePackageUri(uri: Uri?, expectedPackage: String): Boolean {
        return runCatching {
            if (uri == null) return@runCatching false
            val scheme = uri.scheme
            if (scheme != "package") {
                Log.w(TAG, "Unsafe URI scheme: $scheme")
                return@runCatching false
            }
            val ssp = uri.schemeSpecificPart
            if (ssp == null || !ssp.matches(Regex("^[a-zA-Z0-9_.]+$"))) {
                Log.w(TAG, "Unsafe URI ssp: $ssp")
                return@runCatching false
            }
            if (ssp != expectedPackage) {
                Log.w(TAG, "URI package mismatch: expected=$expectedPackage, actual=$ssp")
                return@runCatching false
            }
            true
        }.getOrElse { e ->
            Log.e(TAG, "isSafePackageUri validation failed", e)
            false
        }
    }

    /**
     * 打开应用设置页面，包含 URI 安全验证
     * 防止 Intent URI 注入导致的安全问题
     */
    fun openAppSettings(context: Context) {
        runCatching {
            val packageName = context.packageName
            val uri = Uri.fromParts("package", packageName, null)
            if (!isSafePackageUri(uri, packageName)) {
                Log.e(TAG, "openAppSettings: URI validation failed, aborting")
                return@runCatching
            }
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolveInfo = runCatching {
                context.packageManager.resolveActivity(intent, 0)
            }.getOrNull()
            if (resolveInfo != null) {
                context.startActivity(intent)
                Log.i(TAG, "openAppSettings: launched settings for $packageName")
            } else {
                Log.w(TAG, "openAppSettings: No activity found to handle settings intent")
            }
        }.onFailure { e ->
            Log.e(TAG, "openAppSettings failed", e)
        }
    }

    fun supportsPhotoPicker(): Boolean {
        return runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        }.getOrElse { e ->
            Log.e(TAG, "supportsPhotoPicker failed", e)
            false
        }
    }

    fun needsSafForDirectoryAccess(): Boolean {
        return runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }.getOrElse { e ->
            Log.e(TAG, "needsSafForDirectoryAccess failed", e)
            false
        }
    }

    /**
     * 权限状态审计：详细报告当前所有相关权限状态
     * 用于调试和故障排查，绝不抛出异常
     */
    fun auditPermissionState(context: Context): PermissionAuditResult {
        return runCatching {
            val notes = mutableListOf<String>()
            val granted = mutableListOf<String>()
            val denied = mutableListOf<String>()
            val permanentlyDenied = mutableListOf<String>()
            val activity = context as? Activity

            notes.add("SDK_INT=${Build.VERSION.SDK_INT}")
            notes.add("supportsPhotoPicker=${supportsPhotoPicker()}")
            notes.add("needsSafForDirectoryAccess=${needsSafForDirectoryAccess()}")

            val allPermsToCheck = mutableListOf<String>().apply {
                addAll(getReadMediaPermissions())
                getWritePermission()?.let { add(it) }
                add(Manifest.permission.CAMERA)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.distinct()

            for (perm in allPermsToCheck) {
                when {
                    hasPermission(context, perm) -> {
                        granted.add(perm)
                    }
                    activity != null && isPermanentlyDenied(activity, perm, true) -> {
                        permanentlyDenied.add(perm)
                    }
                    else -> {
                        denied.add(perm)
                    }
                }
            }

            notes.add("hasReadMediaAccess=${hasReadMediaAccess(context)}")
            notes.add("hasPartialMediaAccess=${hasPartialMediaAccess(context)}")
            notes.add("hasWriteAccess=${hasWriteAccess(context)}")

            Log.i(TAG, "auditPermissionState: granted=${granted.size}, denied=${denied.size}, permanentlyDenied=${permanentlyDenied.size}")
            PermissionAuditResult(
                timestamp = System.currentTimeMillis(),
                grantedPermissions = granted,
                deniedPermissions = denied,
                permanentlyDeniedPermissions = permanentlyDenied,
                notes = notes
            )
        }.getOrElse { e ->
            Log.e(TAG, "auditPermissionState failed catastrophically", e)
            PermissionAuditResult(
                timestamp = System.currentTimeMillis(),
                grantedPermissions = emptyList(),
                deniedPermissions = emptyList(),
                permanentlyDeniedPermissions = emptyList(),
                notes = listOf("audit_failed: ${e.javaClass.simpleName}")
            )
        }
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
        runCatching {
            onResult(results)
        }.onFailure { e ->
            Log.e(TAG, "rememberPermissionState: onResult callback failed", e)
        }
    }

    return remember {
        object : PermissionStateHandle {
            override fun requestMediaAccess() {
                runCatching {
                    val permissions = PermissionHelper.getReadMediaPermissions()
                    if (permissions.isEmpty()) {
                        Log.w(TAG, "requestMediaAccess: permission list is empty, launching with empty array")
                    }
                    launcher.launch(permissions.toTypedArray())
                }.onFailure { e ->
                    Log.e(TAG, "requestMediaAccess failed", e)
                }
            }
        }
    }
}
