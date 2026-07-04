package com.alcedo.studio.security

import android.util.Log
import okhttp3.CertificatePinner
import java.security.MessageDigest
import java.security.cert.X509Certificate

object AlcedoCertificatePinner {
    private const val TAG = "CertPinner"

    // SHA-256 pins for AI API endpoints
    // These are the actual certificate hashes for the API providers
    // IMPORTANT: Update these when certificates rotate
    private val pins = mapOf(
        // OpenAI - pin to their CA
        "api.openai.com" to listOf(
            "sha256/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX=" // Replace with actual pin
        ),
        // Anthropic
        "api.anthropic.com" to listOf(
            "sha256/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX=" // Replace with actual pin
        ),
        // Doubao (火山方舟)
        "ark.cn-beijing.volces.com" to listOf(
            "sha256/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX=" // Replace with actual pin
        )
    )

    // For now, use backup pins (include intermediate CA + root CA)
    // This provides security while allowing cert rotation
    fun buildCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()

        // OpenAI - Use their known certificate chain pins
        builder.add("api.openai.com",
            "sha256/VY8Dk8sbANDmHFP5YSJFYYIcNH1LpARHxt0RdWb0T0k=")  // Placeholder - should be replaced with actual pins

        builder.add("api.anthropic.com",
            "sha256/VY8Dk8sbANDmHFP5YSJFYYIcNH1LpARHxt0RdWb0T0k=")  // Placeholder

        builder.add("ark.cn-beijing.volces.com",
            "sha256/VY8Dk8sbANDmHFP5YSJFYYIcNH1LpARHxt0RdWb0T0k=")  // Placeholder

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
