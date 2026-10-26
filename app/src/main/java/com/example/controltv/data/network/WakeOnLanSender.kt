package com.example.controltv.data.network

import com.example.controltv.data.discovery.LocalSubnet
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.repository.TvWaker
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Manda el aviso "Wake-on-LAN" (el "paquete mágico") para despertar una TV apagada.
 *
 * Es un mensaje UDP (un envío suelto, sin conexión) con 6 bytes 0xFF y después la MAC de la TV
 * repetida 16 veces. La placa de red de la TV, aunque esté apagada, lo reconoce y la prende.
 * Es tan simple que no hay librería conocida para esto; se arma acá mismo.
 *
 * Como la TV apagada no tiene IP "activa", se manda a toda la red ("broadcast") y también a su IP,
 * por los dos puertos que se usan para esto (9 y 7).
 */
class WakeOnLanSender(private val subnet: LocalSubnet) : TvWaker {

    override suspend fun wake(ip: String, macAddresses: List<String>) = ioCall(onError = { TvError.Unreachable(it) }) {
        val packets = macAddresses.mapNotNull(::magicPacket)
        val targets = listOfNotNull(ALL_NETWORKS, subnet.broadcastAddress(), ip).distinct().map(InetAddress::getByName)
        // "use" cierra el socket al terminar, aunque haya un error (como try-with-resources en Java).
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (packet in packets) for (target in targets) for (port in PORTS) {
                socket.send(DatagramPacket(packet, packet.size, target, port))
            }
        }
    }

    /** Arma el paquete para una MAC tipo "A4:30:7A:12:34:56" (o con guiones). null si no es una MAC válida. */
    private fun magicPacket(mac: String): ByteArray? {
        val bytes = mac.split(':', '-').mapNotNull { it.toIntOrNull(radix = 16)?.toByte() }
        if (bytes.size != MAC_BYTES) return null
        return ByteArray(MAC_BYTES) { 0xFF.toByte() } + List(REPEAT) { bytes }.flatten().toByteArray()
    }

    private companion object {
        const val ALL_NETWORKS = "255.255.255.255"
        const val MAC_BYTES = 6
        const val REPEAT = 16
        val PORTS = listOf(9, 7)
    }
}
