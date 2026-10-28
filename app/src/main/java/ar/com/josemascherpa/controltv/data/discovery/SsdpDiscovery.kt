package ar.com.josemascherpa.controltv.data.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException

/** SSDP encuentra equipos que anuncian sus servicios, incluso fuera del segmento barrido. */
class SsdpDiscovery {
    suspend fun addresses(): List<String> = withContext(Dispatchers.IO) {
        val found = linkedSetOf<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 200
                val request = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\nMX: 1\r\nST: ssdp:all\r\n\r\n").toByteArray(Charsets.US_ASCII)
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { socket.send(DatagramPacket(request, request.size, group, 1900)) }
                val deadline = System.nanoTime() + 2_200_000_000L
                var received = 0
                while (System.nanoTime() < deadline && received < 256) {
                    currentCoroutineContext().ensureActive()
                    val packet = DatagramPacket(ByteArray(8192), 8192)
                    try {
                        socket.receive(packet)
                        received++
                        val response = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                        if (response.startsWith("HTTP/1.1 200", ignoreCase = true) && packet.address is Inet4Address) {
                            // Sólo usamos al remitente: no seguimos URLs recibidas de la red.
                            packet.address.hostAddress?.let(found::add)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Esperar sin respuesta es normal. El límite total también permite cancelar.
                    }
                }
            }
        } catch (_: IOException) {
            // Hay routers que bloquean multicast: sigue disponible el barrido por IP.
        }
        found.toList()
    }
}
