package com.alcedo.studio.security

import android.util.Log

object NativeSecurityChecker {
    private var libraryLoaded = false

    fun ensureLibraryLoaded(): Boolean {
        if (!libraryLoaded) {
            try {
                System.loadLibrary("alcedo")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e("NativeSecurityChecker", "Failed to load native library", e)
                return false
            }
        }
        return libraryLoaded
    }

    init {
        ensureLibraryLoaded()
    }

    external fun nativeIsDebuggerAttached(): Boolean
    external fun nativeIsRunningInEmulator(): Boolean
    external fun nativeCheckIntegrity(): Boolean
}
