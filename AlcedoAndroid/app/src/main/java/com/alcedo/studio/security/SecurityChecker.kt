package com.alcedo.studio.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import com.alcedo.studio.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime security checks: APK signature verification, debugger/root detection
 * and tamper signals. Mirrors the desktop anti-tamper hooks but adapted to
 * Android's signing model. Results are surfaced to the settings/security screen
 * and gate sensitive operations (credential use, export).
 *
 * The expected release signing-certificate SHA-256 is read from
 * [BuildConfig.ALCEDO_RELEASE_SIGNATURE_SHA256]; it is blank in debug builds
 * (which are signed with the debug key), so signature verification is skipped
 * in debug and enforced in release.
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
        val nativeIntegrityAvailable: Boolean,
        val signatureSha256: String,
    ) {
        val isSecure: Boolean
            get() = signatureOk && nativeIntegrityOk && !debuggerAttached
    }

    /** Run all checks and return a [SecurityReport]. */
    fun report(): SecurityReport {
        val sig = currentSignatureSha256()
        val expected = expectedSignatureSha256()
        // Blank expected hash (debug builds) => verification skipped (passes).
        val sigOk = expected.isBlank() || sig.equals(expected, ignoreCase = true)
        val nativeAvailable = nativeChecker.isNativeAvailable
        return SecurityReport(
            signatureOk = sigOk,
            debuggerAttached = isDebuggerAttached(),
            isRooted = nativeChecker.isRooted(),
            isEmulator = nativeChecker.isEmulator(),
            nativeIntegrityOk = nativeChecker.verifyIntegrity(),
            nativeIntegrityAvailable = nativeAvailable,
            signatureSha256 = sig,
        )
    }

    /** SHA-256 of the current APK signing certificate(s) (hex). */
    fun currentSignatureSha256(): String = runCatching {
        val pm = context.packageManager
        val signers: Array<Signature> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.let { si ->
                    // For the standard (single-signer, possibly rotated) scheme use the
                    // signing certificate history; for multiple-signer schemes use the
                    // current content signers.
                    if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: emptyArray()
        if (signers.isEmpty()) return@runCatching ""
        val md = MessageDigest.getInstance("SHA-256")
        // Hash every signer deterministically so the digest is stable regardless
        // of the order the system returns them in.
        signers.sortedBy { it.hashCode() }.forEach { md.update(it.toByteArray()) }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    /** The expected signing certificate hash; blank in debug builds. */
    fun expectedSignatureSha256(): String = BuildConfig.ALCEDO_RELEASE_SIGNATURE_SHA256

    private fun isDebuggerAttached(): Boolean =
        (context.applicationContext.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            android.os.Debug.isDebuggerConnected()
}
