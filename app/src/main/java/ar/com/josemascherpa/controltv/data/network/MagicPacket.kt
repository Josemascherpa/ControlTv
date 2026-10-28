package ar.com.josemascherpa.controltv.data.network

import ar.com.josemascherpa.controltv.domain.model.MacAddress

/** Formato Wake-on-LAN: seis FF seguidos de dieciséis copias de los seis bytes de la MAC. */
internal fun magicPacket(address: String): ByteArray? {
    val mac = MacAddress.normalize(address) ?: return null
    val bytes = mac.split(':').map { it.toInt(16).toByte() }
    return ByteArray(6) { 0xFF.toByte() } + List(16) { bytes }.flatten().toByteArray()
}
