package com.example.controltv.data.tv.googletv

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory

/**
 * La identidad del teléfono ante las Google TV: una clave RSA y un certificado "autofirmado"
 * (una tarjeta de identidad hecha por nosotros mismos). La TV lo recuerda al emparejar y así
 * no vuelve a pedir el código.
 *
 * Se crea una sola vez y se guarda en un archivo privado de la app. Si se perdiera (por ejemplo,
 * al borrar los datos de la app), habría que volver a emparejar las TVs.
 */
internal class ClientIdentity(private val file: File) {

    private val keyStore: KeyStore by lazy { loadOrCreate() }

    val publicKey: RSAPublicKey get() = keyStore.getCertificate(ALIAS).publicKey as RSAPublicKey

    /** Lo que usa la conexión segura (TLS) para presentar nuestro certificado. */
    fun keyManagers(): Array<KeyManager> =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, PASSWORD) }
            .keyManagers

    // "@Synchronized" evita que dos conexiones a la vez creen dos identidades distintas.
    @Synchronized
    private fun loadOrCreate(): KeyStore {
        val store = KeyStore.getInstance(STORE_TYPE)
        if (file.exists()) {
            file.inputStream().use { store.load(it, PASSWORD) }
            if (store.containsAlias(ALIAS)) return store
        }
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
        store.load(null, null)
        store.setKeyEntry(ALIAS, keyPair.private, PASSWORD, arrayOf(selfSignedCertificate(keyPair)))
        file.outputStream().use { store.store(it, PASSWORD) }
        return store
    }

    // BouncyCastle arma el certificado: quién es (nosotros), hasta cuándo vale, y lo firma con la clave.
    private fun selfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val name = X500Name("CN=$COMMON_NAME")
        val holder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - ONE_DAY_MS),
            Date(now + VALID_DAYS * ONE_DAY_MS),
            name,
            keyPair.public,
        )
            .addExtension(Extension.basicConstraints, false, BasicConstraints(true))
            .build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private companion object {
        const val STORE_TYPE = "PKCS12"
        const val ALIAS = "controltv"
        const val COMMON_NAME = "ControlTv"
        const val KEY_BITS = 2048
        const val VALID_DAYS = 10 * 365
        const val ONE_DAY_MS = 24L * 60 * 60 * 1000

        // El archivo ya es privado de la app; la contraseña es sólo porque el formato la exige.
        val PASSWORD = "controltv".toCharArray()
    }
}
