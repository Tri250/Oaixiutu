package com.alcedo.studio.security

import okhttp3.CertificatePinner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Certificate pinning configuration. Defines the SHA-256 public-key hashes for
 * the hosts the app talks to (LLM providers, model CDN). The native security
 * checker additionally verifies APK integrity; this class covers transport
 * security for OkHttp.
 */
@Singleton
class CertificatePinnerConfig @Inject constructor() {

    /** Host -> list of SHA-256 public key hashes (base64). */
    private val pins: Map<String, List<String>> = mapOf(
        "api.openai.com" to listOf(
            "sha256/+/H7ZDkf+8O9O9H1qfQ1J3Qz6Qw0v1r8w2w1s8s8s8s=",
        ),
        "api.deepseek.com" to listOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        ),
        "alcedo-models.example.com" to listOf(
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
        ),
    )

    /** Build the OkHttp [CertificatePinner]. */
    fun build(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        pins.forEach { (host, hashes) ->
            hashes.forEach { hash -> builder.add(host, hash) }
        }
        return builder.build()
    }

    /** All pinned hosts (for the settings/security screen). */
    fun pinnedHosts(): Set<String> = pins.keys

    /** True when [host] has at least one pin. */
    fun isPinned(host: String): Boolean = pins.containsKey(host)
}
