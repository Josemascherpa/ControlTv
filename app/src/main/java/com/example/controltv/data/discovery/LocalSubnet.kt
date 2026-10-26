package com.example.controltv.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Averigua en qué red WiFi está el teléfono. */
class LocalSubnet(private val context: Context) {

    /**
     * Devuelve "192.168.1." a partir de la IP del teléfono en el WiFi, o null si no hay WiFi.
     *
     * Ojo: la red "principal" del teléfono no siempre es el WiFi. Si el WiFi no tiene internet,
     * o hay una VPN prendida, Android usa los datos móviles (o la VPN) y la app buscaría las TVs
     * en una red equivocada. Por eso buscamos el WiFi a propósito y hacemos que todas las
     * conexiones de la app salgan por ahí (la app sólo habla con TVs de la casa, nunca con internet).
     */
    fun prefix(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = localNetwork(cm) ?: return null
        cm.bindProcessToNetwork(network)
        val address = cm.getLinkProperties(network)
            ?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress ?: return null
        return address.substringBeforeLast('.') + "."
    }

    /** La dirección para hablarle a todos los equipos de la red a la vez (ej. "192.168.1.255"). */
    fun broadcastAddress(): String? = prefix()?.plus("255")

    // La red de la casa: WiFi (o cable, en tablets con adaptador). Si no hay, la principal.
    // allNetworks está marcado como viejo, pero la alternativa (escuchar cambios de red con
    // "callbacks") es mucho más complicada para algo que consultamos una vez por búsqueda.
    @Suppress("DEPRECATION")
    private fun localNetwork(cm: ConnectivityManager): Network? =
        cm.allNetworks.firstOrNull { network ->
            val capabilities = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            // Una VPN también dice "WiFi" si va por encima del WiFi: la salteamos.
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } ?: cm.activeNetwork
}
