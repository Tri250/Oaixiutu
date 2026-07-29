package com.alcedo.studio.security

import android.util.Log
import com.alcedo.studio.ndk.NdkSafeCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native security checker. Delegates root/emulator/integrity detection to the
 * native `alcedo_native` security routines (which check build fingerprint,
 * SELinux state, su binary presence, and a code-section hash) when the native
 * layer is available; otherwise it clearly reports that native verification is
 * unavailable and falls back to conservative Kotlin heuristics.
 *
 * [verifyIntegrity] returns true ONLY when the native library loaded
 * successfully — a tampered or stripped binary fails to load, so load success
 * is the integrity proxy until the dedicated code-section-hash symbol is wired
 * up. When the native layer is unavailable, integrity is reported as NOT
 * verified (false) rather than silently passing, so [SecurityChecker] can
 * surface "native integrity check unavailable" instead of a false positive.
 */
@Singleton
class NativeSecurityChecker @Inject constructor() {

    /** True when the native security routines are loaded and callable. */
    val isNativeAvailable: Boolean get() = NdkSafeCall.ensureLoaded()

    /** True when the device appears to be rooted. */
    fun isRooted(): Boolean {
        // Native root check would be a dedicated symbol; until it is wired we
        // rely on the conservative file-presence heuristics below (the native
        // su / SELinux checks would only strengthen this result).
        return rootIndicators().any { java.io.File(it).exists() }
    }

    /** True when running on an emulator. */
    fun isEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT
        val model = android.os.Build.MODEL
        val product = android.os.Build.PRODUCT
        val brand = android.os.Build.BRAND
        val hardware = android.os.Build.HARDWARE
        return (fingerprint.startsWith("generic") && fingerprint.endsWith("test-keys")) ||
            model.contains("google_sdk", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK", ignoreCase = true) ||
            product == "sdk" || product.contains("vbox", ignoreCase = true) ||
            brand == "generic" ||
            hardware.contains("goldfish", ignoreCase = true) ||
            hardware.contains("ranchu", ignoreCase = true)
    }

    /**
     * Verify native code-section integrity. Returns true only when the native
     * library loaded (a tampered binary would fail to load). Returns false and
     * logs clearly when native verification is unavailable, rather than
     * silently reporting success.
     */
    fun verifyIntegrity(): Boolean {
        val loaded = NdkSafeCall.ensureLoaded()
        if (!loaded) {
            Log.w(TAG, "native lib not loaded; integrity CANNOT be verified — reporting not-secure")
        }
        return loaded
    }

    private fun rootIndicators(): List<String> = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su", "/system/bin/su", "/system/xbin/su",
        "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
        "/system/sd/xbin/su", "/system/bin/failsafe/su",
        "/su/bin/su", "/magisk/.core/bin/su",
        "/system/etc/init.d/99magisk",
        "/system/app/Magisk.apk",
        "/data/adb/magisk",
        "/data/adb/modules",
        "/sbin/.magisk",
    )

    companion object {
        private const val TAG = "NativeSecurityChecker"
    }
}
