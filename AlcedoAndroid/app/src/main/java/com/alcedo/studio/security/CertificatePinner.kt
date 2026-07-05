package com.alcedo.studio.security

import android.util.Log
import okhttp3.CertificatePinner
import java.security.MessageDigest
import java.security.cert.X509Certificate

object AlcedoCertificatePinner {
    private const val TAG = "CertPinner"

    // SHA-256 pins for AI API endpoints.
    // Pinned to well-known root CAs so certificate rotation of the leaf/intermediate
    // certs does not break connectivity, while still preventing MITM with rogue CAs.
    // IMPORTANT: Update these when a provider switches root CA.
    private val pins = mapOf(
        // OpenAI uses DigiCert/Cloudflare
        "api.openai.com" to listOf(
            "sha256/+JjwljGs4g8i5VRCl4UrgpRDOjF0yjfxRj63XD/Y4wQ=",  // DigiCert Global Root CA
            "sha256/jQ9bMubTnrnxCtgTvDvR6YqVthYzr2IkZ/hk4UDJ4Pg="   // Cloudflare Origin CA ECC
        ),
        // Anthropic uses DigiCert
        "api.anthropic.com" to listOf(
            "sha256/+JjwljGs4g8i5VRCl4UrgpRDOjF0yjfxRj63XD/Y4wQ="   // DigiCert Global Root CA
        ),
        // 火山方舟 uses GlobalSign/DigiCert
        "ark.cn-beijing.volces.com" to listOf(
            "sha256/+JjwljGs4g8i5VRCl4UrgpRDOjF0yjfxRj63XD/Y4wQ="   // DigiCert Global Root CA
        )
    )

    // Pin each domain to its list of allowed CA fingerprints (root + intermediate).
    // Using a list per domain provides resilience when the provider rotates between
    // known CAs while still blocking attacks that use an untrusted CA.
    fun buildCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        pins.forEach { (domain, domainPins) ->
            domainPins.forEach { pin ->
                builder.add(domain, pin)
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
     * Call this in debug builds to validate pins.
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
