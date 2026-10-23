package com.example.controltv.data.network

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Los clientes de red para hablar con TVs de la red local.
 * "object" crea una única instancia compartida por toda la app (un singleton).
 */
object NetworkClients {

    // Las TVs usan certificados HTTPS "autofirmados" (no emitidos por una autoridad),
    // así que Android los rechazaría. Como sólo hablamos con dispositivos de la red local,
    // aceptamos cualquier certificado. NO usar estos clientes para internet.
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    /** Para WebSockets (Samsung, LG): la conexión queda abierta, así que no tiene límite de lectura. */
    // "by lazy" = se crea recién la primera vez que alguien lo usa.
    val webSocket: OkHttpClient by lazy {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS) // mantiene viva la conexión
            .build()
    }

    /** Para pedidos HTTP comunes (Roku, Sony, detección): con límite, así una TV muda no cuelga la app. */
    val http: OkHttpClient by lazy {
        webSocket.newBuilder()
            .readTimeout(5, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)
            .build()
    }
}
