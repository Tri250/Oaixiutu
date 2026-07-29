package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for lifecycle / project operations.
 * Matches C++ Java_com_alcedo_studio_ndk_Bridge_*.
 */
@Keep
object Bridge {
    external fun nativeInit(cacheDir: String): Boolean
    external fun nativeShutdown()
    external fun nativeGetVersion(): String
    external fun nativeOpenProject(dbPath: String): Boolean
    external fun nativeCloseProject()
    external fun nativeSaveAll(): Boolean
    external fun nativeIsProjectOpen(): Boolean
    external fun nativeGetProjectPath(): String
}
