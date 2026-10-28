package ar.com.josemascherpa.controltv.data.tv.googletv

import ar.com.josemascherpa.controltv.data.tv.googletv.proto.OuterMessage
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.Options
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.OptionsKt
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.OuterMessageKt
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.configuration
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.options
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.outerMessage
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.pairingRequest
import ar.com.josemascherpa.controltv.data.tv.googletv.proto.secret
import ar.com.josemascherpa.controltv.domain.error.TvError
import com.google.protobuf.ByteString

/**
 * El emparejamiento con una Google TV (puerto 6467), en dos partes:
 *
 * 1. [start]: le pedimos emparejar y acordamos el tipo de código (6 caracteres hexadecimales).
 *    Al terminar, la TV muestra el código en pantalla.
 * 2. [finish]: con el código que escribió la persona calculamos el "secreto" y se lo mandamos.
 *    Si coincide, la TV guarda nuestro certificado y ya no vuelve a pedir código.
 *
 * Bloqueante: usar desde Dispatchers.IO.
 */
internal class GoogleTvPairing(
    private val channel: TlsChannel,
    private val identity: ClientIdentity,
) {
    fun start() {
        channel.send(message { pairingRequest = pairingRequest { serviceName = SERVICE_NAME; clientName = CLIENT_NAME } })
        expect { it.hasPairingRequestAck() }

        channel.send(message { options = options { inputEncodings += HEX_CODE; preferredRole = Options.RoleType.ROLE_TYPE_INPUT } })
        expect { it.hasOptions() }

        channel.send(message { configuration = configuration { encoding = HEX_CODE; clientRole = Options.RoleType.ROLE_TYPE_INPUT } })
        expect { it.hasConfigurationAck() }
    }

    /** @throws TvError.WrongPairingCode si el código no coincide (se puede volver a intentar). */
    fun finish(code: String) {
        val hash = PairingSecret.compute(identity.publicKey, channel.serverPublicKey, code)
            ?: throw TvError.WrongPairingCode()
        channel.send(message { secret = secret { secret = ByteString.copyFrom(hash) } })
        expect { it.hasSecretAck() }
    }

    // Todos los mensajes llevan la versión del protocolo y "todo bien" (200).
    private fun message(build: OuterMessageKt.Dsl.() -> Unit): OuterMessage = outerMessage {
        protocolVersion = PROTOCOL_VERSION
        status = OuterMessage.Status.STATUS_OK
        build()
    }

    /** Espera la respuesta de la TV y comprueba que sea la que corresponde a este paso. */
    private fun expect(isExpected: (OuterMessage) -> Boolean) {
        val reply = channel.receive(OuterMessage::parseDelimitedFrom)
        if (reply.status != OuterMessage.Status.STATUS_OK || !isExpected(reply)) {
            throw TvError.PairingRejected()
        }
    }

    private companion object {
        const val PROTOCOL_VERSION = 2
        const val SERVICE_NAME = "atvremote"
        const val CLIENT_NAME = "ControlTv" // es el nombre que la TV muestra al emparejar

        val HEX_CODE: Options.Encoding = OptionsKt.encoding {
            type = Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL
            symbolLength = 6
        }
    }
}
