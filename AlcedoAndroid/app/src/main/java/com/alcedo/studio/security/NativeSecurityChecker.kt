package com.alcedo.studio.security

object NativeSecurityChecker {
    init {
        System.loadLibrary("alcedo_core")
    }

    external fun nativeIsDebuggerAttached(): Boolean
    external fun nativeIsRunningInEmulator(): Boolean
    external fun nativeCheckIntegrity(): Boolean
}
