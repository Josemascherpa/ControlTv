package ar.com.josemascherpa.controltv.data.discovery

import org.junit.Assert.*
import org.junit.Test

class Ipv4SubnetTest {
    @Test fun `difusion usa la mascara real`() {
        assertEquals("192.168.3.255", Ipv4Subnet("192.168.2.14", 23).broadcast())
        assertEquals("10.2.255.255", Ipv4Subnet("10.2.5.4", 16).broadcast())
        assertEquals("192.168.1.127", Ipv4Subnet("192.168.1.99", 25).broadcast())
    }

    @Test fun `no barre la red ni la difusion ni al propio telefono`() {
        val hosts = Ipv4Subnet("192.168.1.2", 30).hosts()
        assertEquals(listOf("192.168.1.1"), hosts)
    }

    @Test fun `red grande tiene barrido acotado alrededor del telefono`() {
        val hosts = Ipv4Subnet("10.2.5.4", 16).hosts()
        assertEquals(1021, hosts.size)
        assertEquals("10.2.4.1", hosts.first())
        assertEquals("10.2.7.254", hosts.last())
        assertTrue(Ipv4Subnet("10.2.5.4", 32).hosts().isEmpty())
    }
}
