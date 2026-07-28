package com.alcedo.studio.security

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure HTTP client with certificate pinning, timeouts and TLS validation.
 * All network egress (LLM APIs, model downloads, telemetry) goes through the
 * single [client] exposed here. Pinning rules are populated from
 * [CertificatePinner] via [CertificatePinnerConfig].
 */
@Singleton
class SecureHttpClient @Inject constructor(
    private val pinner: CertificatePinnerConfig,
) {
    val client: OkHttpClient by lazy { buildClient() }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .certificatePinner(pinner.build())
            // Reject cleartext traffic at the client level too.
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.isHttps) chain.proceed(req)
                else throw java.io.IOException("Cleartext traffic blocked: ${req.url}")
            }
        return builder.build()
    }
}
