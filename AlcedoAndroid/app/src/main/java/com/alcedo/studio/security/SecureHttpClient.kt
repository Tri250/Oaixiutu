package com.alcedo.studio.security

import android.content.Context
import android.util.Log
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

    /**
     * 强制重建 OkHttpClient 实例，用于证书轮换后刷新 pinning 配置。
     * 下次调用 [getClient] 将返回包含最新 pinning 的新实例。
     */
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

        // Apply certificate pinning
        builder.certificatePinner(AlcedoCertificatePinner.buildCertificatePinner())

        // Apply TLS configuration - only TLS 1.2+ and 1.3
        val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
            .cipherSuites(
                // Only allow strong cipher suites
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

        // Add certificate pinning failure handler
        builder.addInterceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
                Log.e("SecureHttp", "Certificate pinning failure: ${e.message}")
                throw e
            }
        }

        return builder.build()
    }
}
