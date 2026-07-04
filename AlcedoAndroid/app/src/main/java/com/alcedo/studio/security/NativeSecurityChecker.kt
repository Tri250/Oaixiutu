package com.alcedo.studio.security

object NativeSecurityChecker {
    init {
        System.loadLibrary("alcedo")
    }

    external fun nativeIsDebuggerAttached(): Boolean
    external fun nativeIsRunningInEmulator(): Boolean
    external fun nativeCheckIntegrity(): Boolean
}
