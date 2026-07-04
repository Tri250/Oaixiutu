package com.alcedo.studio.security

import android.content.Context
import android.util.Log
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit

object SecureHttpClient {

    private var client: OkHttpClient? = null

    fun getClient(context: Context): OkHttpClient {
        return client ?: synchronized(this) {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

            // Apply certificate pinning
            builder.certificatePinner(AlcedoCertificatePinner.buildCertificatePinner())

            // Apply TLS configuration — enforce TLS 1.3 minimum in release builds
            val tlsVersions = if (com.alcedo.studio.BuildConfig.DEBUG) {
                arrayOf(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
            } else {
                arrayOf(TlsVersion.TLS_1_3)
            }

            val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(*tlsVersions)
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
                    if (com.alcedo.studio.BuildConfig.DEBUG) {
                        Log.e("SecureHttp", "Certificate pinning failure: ${e.message}")
                    }
                    throw e
                }
            }

            builder.build().also { client = it }
        }
    }
}
