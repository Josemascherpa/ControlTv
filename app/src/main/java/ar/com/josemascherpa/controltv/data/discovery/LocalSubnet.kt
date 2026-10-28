package ar.com.josemascherpa.controltv.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.LinkAddress
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
        val address = linkAddress()?.address?.hostAddress ?: return null
        return address.substringBeforeLast('.') + "."
    }

    private fun linkAddress(): LinkAddress? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = localNetwork(cm)
        cm.bindProcessToNetwork(network)
        return network?.let(cm::getLinkProperties)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
    }

    private fun ipv4(): Ipv4Subnet? = linkAddress()?.let { link ->
        link.address.hostAddress?.let { Ipv4Subnet(it, link.prefixLength) }
    }

    fun hosts(): List<String> = ipv4()?.hosts().orEmpty()

    /** La dirección para hablarle a todos los equipos de la red a la vez (ej. "192.168.1.255"). */
    fun broadcastAddress(): String? = ipv4()?.broadcast()

    // La red de la casa: WiFi (o cable, en tablets con adaptador). Nunca los datos móviles.
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
        }
}
