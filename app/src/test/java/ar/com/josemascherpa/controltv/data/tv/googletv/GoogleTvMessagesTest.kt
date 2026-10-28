package ar.com.josemascherpa.controltv.data.tv.googletv

import ar.com.josemascherpa.controltv.data.tv.googletv.proto.RemoteDirection
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.RemoteKeyCode
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.RemoteMessage
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.remoteKeyInject
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.remoteMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class GoogleTvMessagesTest {

    @Test
    fun `un boton viaja con su largo adelante y se lee igual del otro lado`() {
        val sent = remoteMessage {
            remoteKeyInject = remoteKeyInject {
                keyCode = RemoteKeyCode.KEYCODE_HOME
                direction = RemoteDirection.SHORT
            }
        }
        val wire = ByteArrayOutputStream().also { sent.writeDelimitedTo(it) }.toByteArray()

        // El primer byte es el largo del mensaje (lo que espera la TV).
        assertEquals(wire.size - 1, wire[0].toInt())
        val received = RemoteMessage.parseDelimitedFrom(ByteArrayInputStream(wire))
        assertEquals(RemoteKeyCode.KEYCODE_HOME, received.remoteKeyInject.keyCode)
    }

    @Test
    fun `la identidad del telefono se crea una vez y despues se reutiliza`() {
        val dir: File = Files.createTempDirectory("identity").toFile()
        val file = File(dir, "id.p12")

        val first = ClientIdentity(file).publicKey
        val again = ClientIdentity(file).publicKey
        val other = ClientIdentity(File(dir, "otra.p12")).publicKey

        assertTrue(file.exists())
        assertEquals(first, again)
        assertNotEquals(first, other)
        assertTrue(ClientIdentity(file).keyManagers().isNotEmpty())
        dir.deleteRecursively()
    }
}
