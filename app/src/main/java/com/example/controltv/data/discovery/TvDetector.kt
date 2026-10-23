package com.example.controltv.data.discovery

import com.example.controltv.domain.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Reconoce una marca de TV en una IP. Cada marca tiene el suyo en su paquete (data/tv/<marca>),
 * y para sumar una marca nueva sólo hay que agregar uno más a la lista (sin tocar el escáner).
 */
fun interface TvDetector {
    /** Devuelve la TV si en esa IP hay una de esta marca, o null si no. Nunca lanza errores. */
    suspend fun detect(ip: String): TvDevice?
}

/** Prueba si un puerto responde. Que no responda es lo normal en la mayoría de las IPs. */
internal suspend fun isPortOpen(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    try {
        Socket().use { it.connect(InetSocketAddress(ip, port), PORT_TIMEOUT_MS) }
        true
    } catch (e: IOException) {
        false
    }
}

private const val PORT_TIMEOUT_MS = 350
