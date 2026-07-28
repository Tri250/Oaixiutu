package com.alcedo.studio.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime security checks: APK signature verification, debugger/root detection
 * and tamper signals. Mirrors the desktop anti-tamper hooks but adapted to
 * Android's signing model. Results are surfaced to the settings/security screen
 * and gate sensitive operations (credential use, export).
 */
@Singleton
class SecurityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeChecker: NativeSecurityChecker,
) {

    data class SecurityReport(
        val signatureOk: Boolean,
        val debuggerAttached: Boolean,
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val nativeIntegrityOk: Boolean,
        val signatureSha256: String,
    ) {
        val isSecure: Boolean
            get() = signatureOk && nativeIntegrityOk && !debuggerAttached
    }

    /** Run all checks and return a [SecurityReport]. */
    fun report(): SecurityReport {
        val sig = currentSignatureSha256()
        val expected = expectedSignatureSha256()
        val sigOk = expected.isEmpty() || sig.equals(expected, ignoreCase = true)
        return SecurityReport(
            signatureOk = sigOk,
            debuggerAttached = isDebuggerAttached(),
            isRooted = nativeChecker.isRooted(),
            isEmulator = nativeChecker.isEmulator(),
            nativeIntegrityOk = nativeChecker.verifyIntegrity(),
            signatureSha256 = sig,
        )
    }

    /** SHA-256 of the current APK signing certificate (hex). */
    fun currentSignatureSha256(): String = runCatching {
        val pm = context.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: emptyArray()
        val md = MessageDigest.getInstance("SHA-256")
        info.firstOrNull()?.let { sig: Signature -> md.update(sig.toByteArray()) }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    /** The expected signing certificate hash; empty in debug builds. */
    fun expectedSignatureSha256(): String = ""

    private fun isDebuggerAttached(): Boolean =
        (context.applicationContext.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            android.os.Debug.isDebuggerConnected()
}
