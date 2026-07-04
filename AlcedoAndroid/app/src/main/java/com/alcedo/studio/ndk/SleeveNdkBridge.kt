package com.alcedo.studio.ndk

import android.util.Log

object SleeveNdkBridge {
    private const val TAG = "SleeveNdkBridge"

    var isAvailable = false
        private set

    init {
        try {
            System.loadLibrary("alcedo")
            isAvailable = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            isAvailable = false
        }
    }

    external fun nativeInitializeSleeve()
    external fun nativeCreateFolder(path: String, name: String): Int
    external fun nativeCreateFile(path: String, name: String): Int
    external fun nativeDeleteElement(path: String): Boolean
    external fun nativeMoveElement(src: String, dst: String): Boolean
    external fun nativeCopyElement(src: String, dst: String): Boolean
    external fun nativeListFolder(path: String): IntArray?
    external fun nativeResolvePath(path: String): String

    fun initialize() {
        if (!isAvailable) return
        NdkSafeCall.execute("sleeveInitialize") {
            nativeInitializeSleeve()
        }
    }

    fun createFolder(parentPath: String, name: String): Long {
        if (!isAvailable) return -1L
        return NdkSafeCall.execute("createFolder") {
            nativeCreateFolder(parentPath, name).toLong()
        } ?: -1L
    }

    fun createFile(parentPath: String, name: String): Long {
        if (!isAvailable) return -1L
        return NdkSafeCall.execute("createFile") {
            nativeCreateFile(parentPath, name).toLong()
        } ?: -1L
    }

    fun createElement(parentPath: String, name: String, isFolder: Boolean): Long {
        if (!isAvailable) return -1L
        return if (isFolder) {
            createFolder(parentPath, name)
        } else {
            createFile(parentPath, name)
        }
    }

    fun deleteElement(path: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveDeleteElement") {
            nativeDeleteElement(path)
        }
    }

    fun moveElement(src: String, dst: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveMoveElement") {
            nativeMoveElement(src, dst)
        }
    }

    fun copyElement(src: String, dst: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveCopyElement") {
            nativeCopyElement(src, dst)
        }
    }

    fun listFolder(path: String): List<Long> {
        if (!isAvailable) return emptyList()
        val ids = NdkSafeCall.execute("sleeveListFolder") {
            nativeListFolder(path)
        } ?: return emptyList()
        return ids.map { it.toLong() }
    }

    fun resolvePath(path: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("sleeveResolvePath") {
            nativeResolvePath(path)
        }
    }
}
