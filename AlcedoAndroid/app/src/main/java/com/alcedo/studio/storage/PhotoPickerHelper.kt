package com.alcedo.studio.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

/**
 * PhotoPicker 可用性检测结果。
 */
sealed class PhotoPickerAvailability {
    /** 系统原生 Photo Picker（Android 13+） */
    data object SystemPicker : PhotoPickerAvailability()
    /** Google Play Services 提供的 backport（Android 11-12） */
    data object GmsBackport : PhotoPickerAvailability()
    /** 不可用，需回退到 ACTION_GET_CONTENT */
    data object Unavailable : PhotoPickerAvailability()
}

data class PhotoPickerResult(
    val uris: List<Uri>,
    val isPhotoPicker: Boolean
)

object PhotoPickerHelper {

    /**
     * 检测 Photo Picker 在当前设备上的可用性级别。
     *
     * - Android 13+ (API 33): 系统自带 Photo Picker，始终可用。
     * - Android 11-12 (API 30-32): 依赖 Google Play Services 提供的 backport。
     *   通过解析 `com.google.android.gms.picker` 的可用性来判断。
     * - Android 10 及以下: 无 Photo Picker，必须回退到 ACTION_GET_CONTENT。
     */
    fun checkAvailability(context: Context): PhotoPickerAvailability {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                PhotoPickerAvailability.SystemPicker
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11-12：检查 GMS backport 是否可用
                val gmsIntent = Intent("android.provider.action.PICK_IMAGES")
                val resolveInfo = context.packageManager.queryIntentActivities(gmsIntent, 0)
                if (resolveInfo.isNotEmpty()) {
                    PhotoPickerAvailability.GmsBackport
                } else {
                    PhotoPickerAvailability.Unavailable
                }
            }
            else -> {
                PhotoPickerAvailability.Unavailable
            }
        }
    }

    /**
     * 判断 Photo Picker 是否可直接使用（无需回退）。
     */
    fun isAvailable(context: Context): Boolean {
        return checkAvailability(context) != PhotoPickerAvailability.Unavailable
    }
}
