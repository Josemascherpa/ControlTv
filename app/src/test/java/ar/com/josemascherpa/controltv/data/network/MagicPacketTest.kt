package ar.com.josemascherpa.controltv.data.network

import org.junit.Assert.*
import org.junit.Test

class MagicPacketTest {
    @Test fun `paquete contiene cabecera y dieciseis MAC completas`() {
        val packet = requireNotNull(magicPacket("a4-30-7a-12-34-56"))
        assertEquals(102, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        val mac = byteArrayOf(0xA4.toByte(), 0x30, 0x7A, 0x12, 0x34, 0x56)
        repeat(16) { index -> assertArrayEquals(mac, packet.copyOfRange(6 + index * 6, 12 + index * 6)) }
    }

    @Test fun `rechaza bytes fuera de rango y direcciones especiales`() {
        listOf("100:30:7A:12:34:56", "GG:30:7A:12:34:56", "A:30:7A:12:34:56", "00:00:00:00:00:00", "FF:FF:FF:FF:FF:FF", "A4:30:7A:12:34:56:00").forEach {
            assertNull(it, magicPacket(it))
        }
    }
}
