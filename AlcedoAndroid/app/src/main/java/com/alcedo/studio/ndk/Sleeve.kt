package com.alcedo.studio.ndk

import androidx.annotation.Keep

/**
 * JNI bridge for sleeve filesystem operations.
 * Matches C++ Java_com_alcedo_studio_ndk_Sleeve_*.
 */
@Keep
object Sleeve {
    external fun nativeListFolder(folder: String): IntArray
    external fun nativeCreateFolder(parent: String, name: String): Int
    external fun nativeMoveElement(src: String, dest: String): Boolean
    external fun nativeDeleteElement(path: String): Boolean
    external fun nativeFilterFolder(folder: String, sql: String): IntArray
    external fun nativeSearchByText(query: String): IntArray
    external fun nativeGetElementInfo(elementId: Int): String
}
