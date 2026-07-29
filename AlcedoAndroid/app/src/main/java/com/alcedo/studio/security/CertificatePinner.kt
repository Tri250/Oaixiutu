package com.alcedo.studio.security

import com.alcedo.studio.BuildConfig
import okhttp3.CertificatePinner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Certificate pinning configuration. Defines the SHA-256 public-key hashes for
 * the hosts the app talks to (LLM providers, model CDN). The native security
 * checker additionally verifies APK integrity; this class covers transport
 * security for OkHttp.
 *
 * Pin format is the OkHttp "sha256/<base64>" form, where the base64 is the
 * SHA-256 of the DER-encoded SubjectPublicKeyInfo of a certificate in the
 * server's chain. OkHttp accepts a connection if ANY pinned hash matches ANY
 * certificate in the presented chain, so a backup pin (typically the issuing
 * intermediate CA) should always be included to survive leaf rotation.
 *
 * Leaf certificates for the LLM providers rotate frequently; the pins below
 * were captured from the live endpoints and MUST be refreshed (or backed by an
 * intermediate-CA pin) when a provider rotates its certificate, otherwise TLS
 * handshakes will fail. To (re)capture a pin:
 *
 *   echo | openssl s_client -connect HOST:443 -servername HOST 2>/dev/null \
 *     | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | openssl base64
 */
@Singleton
class CertificatePinnerConfig @Inject constructor() {

    /**
     * Host -> list of SHA-256 public key hashes (OkHttp "sha256/<base64>").
     * Entries loaded from BuildConfig for hosts whose live certs could not be
     * baked in are filtered out when blank, so a blank pin disables enforcement
     * for that host instead of breaking it.
     */
    private val pins: Map<String, List<String>> = buildMap {
        // Anthropic — leaf SPKI captured from api.anthropic.com
        // (issuer: Google Trust Services WE1). Add a backup/intermediate pin.
        put(
            "api.anthropic.com",
            listOf("sha256/yzfNb1bRcNF+H1Fts441Vj0MIuuxepdWKmqKJ/bVV6U="),
        )
        // DeepSeek — leaf SPKI captured from api.deepseek.com
        // (issuer: TrustAsia DV TLS RSA CA 2025).
        put(
            "api.deepseek.com",
            listOf("sha256/IS95653JtE1/bNto9qa5E/NHBmBbRDmfaLM+btVVTCk="),
        )
        // OpenAI — injected via BuildConfig so the release pipeline supplies the
        // verified pin. Blank in debug => no enforcement for this host.
        BuildConfig.ALCEDO_PIN_OPENAI.takeIf { it.isNotBlank() }?.let { pin ->
            put("api.openai.com", listOf(pin))
        }
        // HuggingFace model hub + LFS CDN (model downloads).
        BuildConfig.ALCEDO_PIN_HUGGINGFACE.takeIf { it.isNotBlank() }?.let { pin ->
            put("huggingface.co", listOf(pin))
        }
        BuildConfig.ALCEDO_PIN_HF_CDN.takeIf { it.isNotBlank() }?.let { pin ->
            put("cdn-lfs.huggingface.co", listOf(pin))
        }
    }

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
