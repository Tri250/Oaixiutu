package com.alcedo.studio.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.security.MessageDigest

object SecurityChecker {
    private const val TAG = "SecurityChecker"

    // Expected SHA-256 hex of the release signing certificate (set before release)
    private const val EXPECTED_SIGNATURE_HASH = ""

    // ── Debug Detection ──

    fun isDebuggerAttached(): Boolean {
        return android.os.Debug.isDebuggerConnected() ||
               android.os.Debug.waitingForDebugger()
    }

    fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.FINGERPRINT.contains("sdk_gphone") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") ||
                Build.DEVICE.startsWith("generic") ||
                Build.PRODUCT.contains("sdk") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.HARDWARE.contains("vbox") ||
                "google_sdk" == Build.PRODUCT)
    }

    // ── Root Detection ──

    fun isDeviceRooted(): Boolean {
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3() || checkRootMethod4()
    }

    // Check for su binary
    @Keep
    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    // Check for root-related apps and data directories
    @Keep
    private fun checkRootMethod2(): Boolean {
        // Check for root app data directories
        val rootDataDirs = arrayOf(
            "/data/data/com.noshufou.android.su",
            "/data/data/com.thirdparty.superuser",
            "/data/data/eu.chainfire.supersu",
            "/data/data/com.koushikdutta.superuser",
            "/data/data/com.topjohnwu.magisk",
            "/data/user_de/0/com.topjohnwu.magisk"
        )
        if (rootDataDirs.any { File(it).exists() }) return true

        // Check for su daemon socket
        val suSocketPaths = arrayOf(
            "/dev/com.koushikdutta.superuser.daemon",
            "/dev/com.noshufou.android.su.daemon",
            "/dev/com.topjohnwu.magisk.daemon"
        )
        if (suSocketPaths.any { File(it).exists() }) return true

        // Check for Magisk files and directories
        val magiskPaths = arrayOf(
            "/sbin/.magisk",
            "/cache/.disable_magisk",
            "/dev/.magisk.unblock",
            "/data/adb/magisk",
            "/data/adb/modules"
        )
        if (magiskPaths.any { File(it).exists() }) return true

        return false
    }

    // Check for dangerous properties
    @Keep
    private fun checkRootMethod3(): Boolean {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val input = process.inputStream.bufferedReader().readText()
            if (input.isNotEmpty()) return true
        } catch (e: Exception) {
            // Expected on non-rooted devices
        }
        return false
    }

    // Check for additional root indicators: dangerous props, su daemon socket
    @Keep
    private fun checkRootMethod4(): Boolean {
        // Check for dangerous system properties that indicate root
        try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.debuggable"))
            val debuggable = process.inputStream.bufferedReader().readText().trim()
            if (debuggable == "1") return true

            val secureProcess = Runtime.getRuntime().exec(arrayOf("getprop", "ro.secure"))
            val secure = secureProcess.inputStream.bufferedReader().readText().trim()
            if (secure == "0") return true

            val selinuxProcess = Runtime.getRuntime().exec(arrayOf("getprop", "ro.build.selinux"))
            val selinux = selinuxProcess.inputStream.bufferedReader().readText().trim()
            if (selinux.isEmpty()) return true
        } catch (_: Exception) {
            // Property checks may fail without shell access
        }

        // Check for su daemon socket file
        try {
            val socketFile = File("/dev/com.topjohnwu.magisk.daemon")
            if (socketFile.exists()) return true
        } catch (_: Exception) {
            // File access may be restricted
        }

        // Check for additional root-related files
        val additionalRootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk",
            "/system/app/Magisk.apk",
            "/system/etc/init.d",
            "/system/xbin/daemonsu"
        )
        return additionalRootPaths.any { File(it).exists() }
    }

    // ── App Integrity Check ──

    @Keep
    fun verifyAppSignature(context: Context): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return false

            // Compute SHA-256 of the signing certificate
            val signature = signatures[0]
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(signature.toByteArray())
            val hexHash = hash.joinToString("") { "%02x".format(it) }

            // In production, compare against expected signing certificate hash
            if (EXPECTED_SIGNATURE_HASH.isNotEmpty()) {
                hexHash.equals(EXPECTED_SIGNATURE_HASH, ignoreCase = true)
            } else {
                // Before EXPECTED_SIGNATURE_HASH is configured, just verify a signature exists
                hexHash.isNotEmpty()
            }
        } catch (e: Exception) {
            if (com.alcedo.studio.BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to verify app signature", e)
            }
            false
        }
    }

    // ── Anti-Tampering: Check for repackaging ──

    @Keep
    fun checkInstallerValidity(context: Context): Boolean {
        return try {
            val installer = context.packageManager.getInstallerPackageName(context.packageName)
            // In debug builds, allow null installer (side-loaded via IDE)
            if (com.alcedo.studio.BuildConfig.DEBUG) return true
            // In release, the installer should be a legitimate store
            installer != null
        } catch (_: Exception) {
            !com.alcedo.studio.BuildConfig.DEBUG
        }
    }

    // ── Security Status ──

    data class SecurityStatus(
        val isDebuggable: Boolean,
        val isDebuggerAttached: Boolean,
        val isEmulator: Boolean,
        val isRooted: Boolean,
        val signatureValid: Boolean,
        val installerValid: Boolean,
        val isSecure: Boolean
    )

    @Keep
    fun checkSecurity(context: Context): SecurityStatus {
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isDebuggerAttached = isDebuggerAttached()
        val isEmulator = isRunningOnEmulator()
        val isRooted = isDeviceRooted()
        val signatureValid = verifyAppSignature(context)
        val installerValid = checkInstallerValidity(context)

        val isSecure = (!isDebuggable || com.alcedo.studio.BuildConfig.DEBUG) &&
                       !isDebuggerAttached &&
                       (!isEmulator || com.alcedo.studio.BuildConfig.DEBUG) &&
                       !isRooted &&
                       signatureValid &&
                       installerValid

        // Only log security status in debug builds — never expose in production
        if (com.alcedo.studio.BuildConfig.DEBUG) {
            Log.d(TAG, "Security check: debuggable=$isDebuggable, debugger=$isDebuggerAttached, " +
                    "emulator=$isEmulator, rooted=$isRooted, sigValid=$signatureValid, " +
                    "installerValid=$installerValid, secure=$isSecure")
        }

        return SecurityStatus(
            isDebuggable = isDebuggable,
            isDebuggerAttached = isDebuggerAttached,
            isEmulator = isEmulator,
            isRooted = isRooted,
            signatureValid = signatureValid,
            installerValid = installerValid,
            isSecure = isSecure
        )
    }
}
