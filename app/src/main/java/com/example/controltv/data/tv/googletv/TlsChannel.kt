package com.example.controltv.data.tv.googletv

import com.example.controltv.data.network.NetworkClients
import com.google.protobuf.MessageLite
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Una conexión segura (TLS) con la Google TV, por la que van y vienen mensajes protobuf.
 * Cada mensaje viaja con su largo adelante ("delimited"); de eso se encarga protobuf.
 * Todo acá es "bloqueante": hay que usarlo desde Dispatchers.IO.
 */
internal class TlsChannel private constructor(private val socket: SSLSocket) : Closeable {

    private val input = socket.inputStream
    private val output = socket.outputStream

    /** La clave pública de la TV: hace falta para comprobar el código de emparejamiento. */
    val serverPublicKey: RSAPublicKey
        get() = (socket.session.peerCertificates.first() as X509Certificate).publicKey as RSAPublicKey

    // "synchronized" = de a uno por vez: el hilo que lee también contesta (pings) mientras
    // la app manda botones, y dos mensajes mezclados romperían la conexión.
    fun send(message: MessageLite) = synchronized(output) {
        message.writeDelimitedTo(output)
        output.flush()
    }

    /**
     * Espera el próximo mensaje. [parse] es la función de protobuf del tipo esperado,
     * por ejemplo OuterMessage::parseDelimitedFrom (devuelve null si la TV cerró la conexión).
     * @throws IOException si se corta la conexión.
     */
    fun <T : MessageLite> receive(parse: (InputStream) -> T?): T =
        parse(input) ?: throw EOFException("la TV cerró la conexión")

    override fun close() {
        try {
            socket.close()
        } catch (e: IOException) {
            // ya estaba cerrada: no hay nada que hacer
        }
    }

    companion object {
        /**
         * Conecta y se presenta con nuestro certificado.
         * @throws javax.net.ssl.SSLException si la TV no reconoce nuestro certificado (hay que emparejar).
         * @throws IOException si no se pudo conectar.
         */
        fun open(ip: String, port: Int, identity: ClientIdentity, readTimeoutMs: Int): TlsChannel {
            // La TV usa un certificado propio (no emitido por una autoridad), igual que las demás marcas.
            val context = SSLContext.getInstance("TLS").apply {
                init(identity.keyManagers(), arrayOf(NetworkClients.trustAll), SecureRandom())
            }
            val socket = context.socketFactory.createSocket() as SSLSocket
            try {
                socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = readTimeoutMs
                socket.startHandshake()
            } catch (e: IOException) {
                socket.close()
                throw e
            }
            return TlsChannel(socket)
        }

        private const val CONNECT_TIMEOUT_MS = 4_000
    }
}
