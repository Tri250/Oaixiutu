package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for edit history operations.
 * Matches C++ Java_com_alcedo_studio_ndk_History_*.
 */
@Keep
object History {
    external fun nativeUndo(fileId: Int): Boolean
    external fun nativeRedo(fileId: Int): Boolean
    external fun nativeGetVersionCount(fileId: Int): Int
    external fun nativeCreateVersion(fileId: Int, displayName: String): Int
    external fun nativeSwitchVersion(fileId: Int, versionLo: Long, versionHi: Long): Boolean
    external fun nativeGetHistoryJson(fileId: Int): String
    external fun nativeRenameVersion(fileId: Int, versionLo: Long, versionHi: Long, name: String)
}
