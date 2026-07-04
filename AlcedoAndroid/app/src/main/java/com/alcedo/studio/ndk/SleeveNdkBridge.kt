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

    external fun nativeSleeveInitialize(): Boolean
    external fun nativeSleeveGetElement(path: String): String
    external fun nativeSleeveCreateElement(parentPath: String, name: String, type: Int): Long
    external fun nativeSleeveDeleteElement(path: String): Boolean
    external fun nativeSleeveCopyElement(src: String, dest: String): Boolean
    external fun nativeSleeveMoveElement(src: String, dest: String): Boolean
    external fun nativeSleeveListFolder(path: String): String
    external fun nativeSleeveGetPipelineInfo(): String

    fun initialize(): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveInitialize") {
            nativeSleeveInitialize()
        }
    }

    fun getElement(path: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("sleeveGetElement") {
            nativeSleeveGetElement(path)
        }
    }

    fun createElement(parentPath: String, name: String, isFolder: Boolean): Long {
        if (!isAvailable) return -1L
        return NdkSafeCall.execute("sleeveCreateElement") {
            nativeSleeveCreateElement(parentPath, name, if (isFolder) 2 else 1)
        } ?: -1L
    }

    fun deleteElement(path: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveDeleteElement") {
            nativeSleeveDeleteElement(path)
        }
    }

    fun copyElement(src: String, dest: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveCopyElement") {
            nativeSleeveCopyElement(src, dest)
        }
    }

    fun moveElement(src: String, dest: String): Boolean {
        if (!isAvailable) return false
        return NdkSafeCall.executeBoolean("sleeveMoveElement") {
            nativeSleeveMoveElement(src, dest)
        }
    }

    fun listFolder(path: String): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("sleeveListFolder") {
            nativeSleeveListFolder(path)
        }
    }

    fun getPipelineInfo(): String? {
        if (!isAvailable) return null
        return NdkSafeCall.execute("sleeveGetPipelineInfo") {
            nativeSleeveGetPipelineInfo()
        }
    }
}
