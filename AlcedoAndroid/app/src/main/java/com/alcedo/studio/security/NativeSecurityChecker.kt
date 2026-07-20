package com.alcedo.studio.security

import android.util.Log

object NativeSecurityChecker {
    private const val TAG = "NativeSecurityChecker"
    private var libraryLoaded = false

    fun ensureLibraryLoaded(): Boolean {
        if (!libraryLoaded) {
            try {
                System.loadLibrary("alcedo")
                libraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                return false
            } catch (e: ExceptionInInitializerError) {
                Log.e(TAG, "Native library static initializer failed", e)
                return false
            }
        }
        return libraryLoaded
    }

    init {
        ensureLibraryLoaded()
    }

    /**
     * 修复: 原直接声明 external fun 在库加载失败时会抛 UnsatisfiedLinkError 导致崩溃。
     * 现改为安全包装: 如果库未加载则返回安全的默认值，而非崩溃。
     */
    fun nativeIsDebuggerAttached(): Boolean {
        if (!libraryLoaded) {
            Log.w(TAG, "Native library not loaded, returning false for nativeIsDebuggerAttached")
            return false
        }
        return try {
            nativeIsDebuggerAttachedImpl()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError in nativeIsDebuggerAttached", e)
            false
        }
    }

    fun nativeIsRunningInEmulator(): Boolean {
        if (!libraryLoaded) {
            Log.w(TAG, "Native library not loaded, returning false for nativeIsRunningInEmulator")
            return false
        }
        return try {
            nativeIsRunningInEmulatorImpl()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError in nativeIsRunningInEmulator", e)
            false
        }
    }

    fun nativeCheckIntegrity(): Boolean {
        if (!libraryLoaded) {
            Log.w(TAG, "Native library not loaded, returning true for nativeCheckIntegrity")
            return true
        }
        return try {
            nativeCheckIntegrityImpl()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "UnsatisfiedLinkError in nativeCheckIntegrity", e)
            true
        }
    }

    private external fun nativeIsDebuggerAttachedImpl(): Boolean
    private external fun nativeIsRunningInEmulatorImpl(): Boolean
    private external fun nativeCheckIntegrityImpl(): Boolean
}
