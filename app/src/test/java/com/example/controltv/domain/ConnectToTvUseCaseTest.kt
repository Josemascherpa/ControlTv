package com.example.controltv.domain

import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.KnownTv
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.session.TvSession
import com.example.controltv.domain.usecase.ConnectToTvUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectToTvUseCaseTest {

    private val credentials = FakeCredentials()
    private val knownTvs = FakeKnownTvs()
    private val paired = FakePaired()
    private val session = TvSession()

    private fun useCase(tv: FakeTv) = ConnectToTvUseCase({ tv }, credentials, knownTvs, paired, session)

    @Test
    fun `conecta, deja la TV como actual y la recuerda`() = runBlocking {
        val tv = FakeTv(TvDevice("192.168.1.10", "Living", TvBrand.SAMSUNG))

        useCase(tv)(tv.device)

        assertTrue(tv.connected)
        assertSame(tv, session.controller.value)
        assertEquals(tv.device, knownTvs.all().first().device)
    }

    @Test
    fun `una Google TV nueva necesita la guia, pero ya no despues de conectarse una vez`() = runBlocking {
        val tv = FakeTv(TvDevice("192.168.1.30", "Google TV", TvBrand.ANDROID))
        val connect = useCase(tv)

        assertTrue(connect.needsSetup(tv.device))
        connect(tv.device)
        assertFalse(connect.needsSetup(tv.device))
    }

    @Test
    fun `las TVs que no usan depuracion nunca necesitan la guia`() {
        val tv = FakeTv(TvDevice("192.168.1.10", "Living", TvBrand.SAMSUNG))
        assertFalse(useCase(tv).needsSetup(tv.device))
    }

    @Test(expected = TvError.MissingKey::class)
    fun `una Sony sin clave pide la clave antes de intentar conectar`(): Unit = runBlocking {
        val tv = FakeTv(TvDevice("192.168.1.20", "Sony", TvBrand.SONY))
        useCase(tv)(tv.device)
    }

    @Test
    fun `si pasan la clave la guarda y conecta`() = runBlocking {
        val tv = FakeTv(TvDevice("192.168.1.20", "Sony", TvBrand.SONY))

        useCase(tv)(tv.device, key = " 1234 ")

        assertEquals("1234", credentials.saved["192.168.1.20"])
        assertTrue(tv.connected)
    }

    @Test
    fun `si falla la conexion cierra el controlador y no lo deja como actual`() = runBlocking {
        val tv = FakeTv(TvDevice("192.168.1.10", "Living", TvBrand.LG), failOnConnect = TvError.PairingTimeout())

        val error = runCatching { useCase(tv)(tv.device) }.exceptionOrNull()

        assertTrue(error is TvError.PairingTimeout)
        assertTrue(tv.disconnected)
        assertNull(session.controller.value)
        assertTrue(knownTvs.all().isEmpty())
    }

    @Test
    fun `Google TV pide el codigo, deja reintentar si esta mal y conecta con el correcto`() = runBlocking {
        val tv = FakeCodeTv(TvDevice("192.168.1.40", "Google TV", TvBrand.GOOGLE_TV), rightCode = "4A21F0")
        val connect = useCase(tv)

        val first = runCatching { connect(tv.device) }.exceptionOrNull()
        assertTrue(first is TvError.PairingCodeRequired)
        assertNull(session.controller.value)

        val wrong = runCatching { connect.submitPairingCode("000000") }.exceptionOrNull()
        assertTrue(wrong is TvError.WrongPairingCode)
        assertTrue(!tv.disconnected) // sigue esperando el código

        connect.submitPairingCode("4A21F0")
        assertTrue(tv.connected)
        assertSame(tv, session.controller.value)
    }

    @Test
    fun `cancelar el codigo suelta la TV`() = runBlocking {
        val tv = FakeCodeTv(TvDevice("192.168.1.40", "Google TV", TvBrand.GOOGLE_TV), rightCode = "4A21F0")
        val connect = useCase(tv)
        runCatching { connect(tv.device) }

        connect.cancelPairing()

        assertTrue(tv.disconnected)
        val error = runCatching { connect.submitPairingCode("4A21F0") }.exceptionOrNull()
        assertTrue(error is TvError.NotConnected)
    }

    @Test
    fun `al reconectar una TV conocida usa el nombre que le puso la persona`() = runBlocking {
        val found = TvDevice("192.168.1.10", "LG webOS TV", TvBrand.LG)
        knownTvs.save(KnownTv(found.copy(name = "TV del living")))
        // Como la fábrica de verdad: el controlador se crea con la TV que le pasan.
        ConnectToTvUseCase({ FakeTv(it) }, credentials, knownTvs, paired, session)(found)

        assertEquals("TV del living", session.current().device.name)
        assertEquals("TV del living", knownTvs.all().single().device.name)
    }

    @Test
    fun `si en esa IP ahora hay otra marca, no le pone el nombre guardado`() = runBlocking {
        knownTvs.save(KnownTv(TvDevice("192.168.1.10", "TV del living", TvBrand.LG)))
        val found = TvDevice("192.168.1.10", "Samsung TV", TvBrand.SAMSUNG)

        useCase(FakeTv(found))(found)

        assertEquals("Samsung TV", knownTvs.all().single().device.name)
    }
}
