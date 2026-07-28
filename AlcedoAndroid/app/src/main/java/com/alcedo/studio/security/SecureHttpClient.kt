package com.alcedo.studio.security

import android.content.Context
import android.util.Log
import com.alcedo.studio.BuildConfig
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

object SecureHttpClient {

    @Volatile
    private var client: OkHttpClient? = null
    private val lock = Any()

    fun getClient(context: Context): OkHttpClient {
        return client ?: synchronized(lock) {
            client ?: buildClient().also { client = it }
        }
    }

    fun refreshClient() {
        synchronized(lock) {
            client = null
        }
    }

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        // Certificate pinning: only enforced in release builds.
        // Debug builds skip pinning to allow development against
        // staging/self-signed servers without SSLPeerUnverifiedException crashes.
        if (!BuildConfig.DEBUG) {
            builder.certificatePinner(AlcedoCertificatePinner.buildCertificatePinner())
        } else {
            Log.i("SecureHttp", "DEBUG build: certificate pinning disabled")
        }

        val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
            .cipherSuites(
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_AES_256_GCM_SHA384,
                CipherSuite.TLS_AES_128_GCM_SHA256,
                CipherSuite.TLS_CHACHA20_POLY1305_SHA256
            )
            .build()

        builder.connectionSpecs(listOf(connectionSpec))

        return builder.build()
    }
}
