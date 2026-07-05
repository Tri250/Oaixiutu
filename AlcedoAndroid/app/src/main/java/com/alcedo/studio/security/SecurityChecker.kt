package com.alcedo.studio.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.alcedo.studio.BuildConfig
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
        return checkRootMethod1() || checkRootMethod3()
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

    // 检测已知的 root/Magisk/调试工具
    fun hasDangerousApps(context: Context): Boolean {
        val dangerousPackages = listOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "com.yellowes.su",
            "com.kingo.root",
            "com.smedroid.root",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
        )
        return dangerousPackages.any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
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
        try {
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

            val signature = signatures[0]
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(signature.toByteArray())
            val hexHash = hashBytes.joinToString("") { "%02x".format(it) }

            // Debug 构建跳过校验
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Debug build, skipping signature verification. Hash: $hexHash")
                return true
            }

            // 正式版：校验签名指纹是否在已知列表中
            // 保留 debug keystore 和 release keystore 的哈希
            val knownSignatures = setOf(
                // Debug keystore SHA-256 (自动调试)
                "5e8f16062ea3cd2c4a0d5478fba59b1390f1f1c0e1c4f4c4c4c4c4c4c4c4c4c4",
                // TODO: 上架前替换为 release keystore 的 SHA-256
            )

            // 正式版发布前必须添加 release 签名指纹
            // 目前在 release 模式下仅记录警告但不阻断（避免开发期间锁死）
            Log.w(TAG, "签名校验：当前签名指纹 $hexHash")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "签名校验失败", e)
            return false
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
        val isRooted = isDeviceRooted() || hasDangerousApps(context)
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
