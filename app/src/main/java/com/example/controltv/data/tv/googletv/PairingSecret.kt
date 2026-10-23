package com.example.controltv.data.tv.googletv

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * El "secreto" del emparejamiento. La TV muestra un código de 6 caracteres hexadecimales
 * (0-9 y A-F), por ejemplo "4A21F0", que sale de mezclar los certificados del teléfono y la TV:
 *
 *   SHA-256( clave del teléfono + clave de la TV + últimos 4 caracteres del código )
 *
 * Los 2 primeros caracteres del código son el primer byte de ese resultado: así podemos
 * comprobar acá mismo si la persona lo escribió bien, antes de mandarlo.
 */
internal object PairingSecret {

    /** Devuelve el secreto para mandarle a la TV, o null si el código está mal escrito. */
    fun compute(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
        val normalized = code.trim().uppercase()
        if (!CODE_FORMAT.matches(normalized)) return null

        val digest = MessageDigest.getInstance("SHA-256").run {
            update(client.modulus.unsignedBytes())
            update(client.publicExponent.unsignedBytes())
            update(server.modulus.unsignedBytes())
            update(server.publicExponent.unsignedBytes())
            update(hexToBytes(normalized.substring(2)))
            digest()
        }
        val expectedCheck = normalized.substring(0, 2).toInt(16)
        return if (digest[0].toInt() and 0xFF == expectedCheck) digest else null
    }

    // BigInteger.toByteArray() agrega un 0 adelante cuando el primer bit está prendido (para el signo).
    // El protocolo usa el número sin ese 0.
    private fun BigInteger.unsignedBytes(): ByteArray {
        val bytes = toByteArray()
        return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private val CODE_FORMAT = Regex("[0-9A-F]{6}")
}
