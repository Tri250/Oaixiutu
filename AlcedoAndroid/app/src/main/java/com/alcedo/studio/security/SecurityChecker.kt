package com.alcedo.studio.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest

object SecurityChecker {
    private const val TAG = "SecurityChecker"

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
        return checkRootMethod1() || checkRootMethod2() || checkRootMethod3()
    }

    // Check for su binary
    private fun checkRootMethod1(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    // Check for root-related apps
    private fun checkRootMethod2(): Boolean {
        val packages = arrayOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.topjohnwu.magisk"
        )
        // Can't check installed packages without query intent, use file check instead
        return false // Simplified - actual check would use PackageManager
    }

    // Check for dangerous properties
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

    // ── App Integrity Check ──

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

            // In production, compare with known good hash
            // For now, just verify a signature exists
            hexHash.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify app signature", e)
            false
        }
    }

    // ── Security Status ──

    data class SecurityStatus(
        val isDebuggable: Boolean,
        val isDebuggerAttached: Boolean,
        val isEmulator: Boolean,
        val isRooted: Boolean,
        val signatureValid: Boolean,
        val isSecure: Boolean
    )

    fun checkSecurity(context: Context): SecurityStatus {
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isDebuggerAttached = isDebuggerAttached()
        val isEmulator = isRunningOnEmulator()
        val isRooted = isDeviceRooted()
        val signatureValid = verifyAppSignature(context)

        val isSecure = !isDebuggable && !isDebuggerAttached && !isRooted && signatureValid

        return SecurityStatus(
            isDebuggable = isDebuggable,
            isDebuggerAttached = isDebuggerAttached,
            isEmulator = isEmulator,
            isRooted = isRooted,
            signatureValid = signatureValid,
            isSecure = isSecure
        )
    }
}
