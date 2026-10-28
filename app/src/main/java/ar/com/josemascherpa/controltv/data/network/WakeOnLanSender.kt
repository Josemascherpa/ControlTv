package ar.com.josemascherpa.controltv.data.network

import ar.com.josemascherpa.controltv.data.discovery.LocalSubnet
import ar.com.josemascherpa.controltv.domain.error.TvError
import ar.com.josemascherpa.controltv.domain.repository.TvWaker
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
        if (packets.isEmpty()) throw TvError.CantTurnOn()
        val targets = listOfNotNull(ALL_NETWORKS, subnet.broadcastAddress(), ip).distinct().map(InetAddress::getByName)
        // "use" cierra el socket al terminar, aunque haya un error (como try-with-resources en Java).
        DatagramSocket().use { socket ->
            socket.broadcast = true
            var sent = false
            for (packet in packets) for (target in targets) for (port in PORTS) {
                try {
                    socket.send(DatagramPacket(packet, packet.size, target, port))
                    sent = true
                } catch (_: java.io.IOException) {
                    // Algunos routers bloquean la difusión global: seguimos con la local y la IP.
                }
            }
            if (!sent) throw TvError.Unreachable()
        }
    }

    private companion object {
        const val ALL_NETWORKS = "255.255.255.255"
        val PORTS = listOf(9, 7)
    }
}
