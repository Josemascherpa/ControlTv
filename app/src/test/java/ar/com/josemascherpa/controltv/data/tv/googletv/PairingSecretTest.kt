package ar.com.josemascherpa.controltv.data.tv.googletv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

/**
 * Los valores esperados salen de correr el mismo cálculo con la implementación de referencia en
 * Python (github.com/tronikos/androidtvremote2, pairing.py). Si coinciden, nuestro código manda
 * exactamente el mismo secreto que una implementación que ya funciona con TVs reales.
 */
class PairingSecretTest {

    private val client = rsaKey(CLIENT_MODULUS)
    private val server = rsaKey(SERVER_MODULUS)

    @Test
    fun `calcula el mismo secreto que la implementacion de referencia`() {
        val secret = PairingSecret.compute(client, server, "07A1B2")
        assertEquals(EXPECTED_HASH, secret?.toHex())
    }

    @Test
    fun `acepta el codigo escrito en minuscula y con espacios`() {
        assertEquals(EXPECTED_HASH, PairingSecret.compute(client, server, " 07a1b2 ")?.toHex())
    }

    @Test
    fun `rechaza un codigo mal copiado`() {
        assertNull(PairingSecret.compute(client, server, "08A1B2"))
        assertNull(PairingSecret.compute(client, server, "07A1B3"))
    }

    @Test
    fun `rechaza lo que no tiene formato de codigo`() {
        assertNull(PairingSecret.compute(client, server, "07A1B"))
        assertNull(PairingSecret.compute(client, server, "07A1BZ"))
        assertNull(PairingSecret.compute(client, server, ""))
    }

    private fun rsaKey(modulusHex: String): RSAPublicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(BigInteger(modulusHex, 16), BigInteger.valueOf(65537))) as RSAPublicKey

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    private companion object {
        const val CLIENT_MODULUS =
            "F59CDE66BACFB3D00B1F9163CE9FF57F43B7A3A69A8DCA03580D7B71D8F564135BE6128E18C267976142EA7D17BE31111A2A73ED562B0F79C37459EEF50BEA63371ECD7B27CD813047229389571AA8766C307511B2B9437A28DF6EC4CE4A2BBDC241330B01A9E71FDE8A774BCF36D58B4737819096DA1DAC72FF5D2A386ECBE06B65A6A48B8148F6B38A088CA65ED389B74D0FB132E706298FADC1A606CB0FB39A1DE644815EF6D13B8FAA1837F8A88B17FC695A07A0CA6E0822E8F36C031199972A846916419F828B9D2434E465E150BD9C66B3AD3C2D6D1A3D1FA7BC8960A923B8C1E9392456DE3EB13B9046685257BDD640FB06671AD11C80317FA3B1799D"
        const val SERVER_MODULUS =
            "D304317FAF42E12F3838B3268E944239B02B61C4A3D70628ECE66FA2FD5166E6451B4CF36123FDF77656AF7229D4BEEF3EABEDCBBAA80DD488BD64072BCFBE01A28DEFE39BF0027312476F57A5E5A5ABAEFCFAD8EFC89849B3AA7EFE4458A885AB9099A435A240AE5AF305535EC42E0829A3B2E95D65A441D58842DEA2BC372F7412B29347294739614FF3D719DB3AD0DDD1DFB23B982EF8DAF61A26146D3F31FC377A4C4A15544DC5E7CE8A3A578A8EA9488D990BBB259911CE5DD2B45ED1F03139D32C93CD59BF5C941CF0DC98D2C1E2ACF72F9E574F7AA0EE89AED453DD324B0DBB418D5288F1142C3FE860E7A113EC1B8CA1F91E1D4C1FF49B7889463E85"
        const val EXPECTED_HASH = "070C74675FB80A5811271C74BC78C09AC5B4F353EF76E87A52579A11B886485A"
    }
}
