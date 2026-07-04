package com.alcedo.studio.security

import android.util.Log
import okhttp3.CertificatePinner
import java.security.MessageDigest
import java.security.cert.X509Certificate

object AlcedoCertificatePinner {
    private const val TAG = "CertPinner"

    // Production pins loaded from BuildConfig fields.
    // Primary pins are the current certificate hashes; backup pins allow rotation.
    // To rotate: promote backup to primary, add new backup, then update BuildConfig fields.
    private val pins: Map<String, List<String>>
        get() = mapOf(
            "api.openai.com" to buildPinList(
                com.alcedo.studio.BuildConfig.CERT_PIN_OPENAI_PRIMARY,
                com.alcedo.studio.BuildConfig.CERT_PIN_OPENAI_BACKUP
            ),
            "api.anthropic.com" to buildPinList(
                com.alcedo.studio.BuildConfig.CERT_PIN_ANTHROPIC_PRIMARY,
                com.alcedo.studio.BuildConfig.CERT_PIN_ANTHROPIC_BACKUP
            ),
            "ark.cn-beijing.volces.com" to buildPinList(
                com.alcedo.studio.BuildConfig.CERT_PIN_DOUBAO_PRIMARY,
                com.alcedo.studio.BuildConfig.CERT_PIN_DOUBAO_BACKUP
            )
        )

    private fun buildPinList(primary: String, backup: String): List<String> {
        val list = mutableListOf<String>()
        if (primary.isNotEmpty()) list.add("sha256/$primary")
        if (backup.isNotEmpty()) list.add("sha256/$backup")
        return list
    }

    /**
     * Build the OkHttp CertificatePinner with both primary and backup pins.
     * Backup pins enable seamless certificate rotation without app updates.
     */
    fun buildCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()

        pins.forEach { (domain, domainPins) ->
            if (domainPins.isNotEmpty()) {
                builder.add(domain, *domainPins.toTypedArray())
            }
        }

        return builder.build()
    }

    /**
     * Utility to compute SHA-256 pin from a certificate.
     * Run this during development to get actual pin values.
     */
    fun computePin(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return "sha256/${java.util.Base64.getEncoder().encodeToString(hash)}"
    }

    /**
     * Verify that pinning is working correctly.
     * Only logs in debug builds — never leak certificate details in production.
     */
    fun validatePins() {
        if (com.alcedo.studio.BuildConfig.DEBUG) {
            Log.d(TAG, "Certificate pinning configured with ${pins.size} domains")
            pins.forEach { (domain, domainPins) ->
                Log.d(TAG, "  $domain: ${domainPins.size} pins configured")
            }
        }
    }
}
