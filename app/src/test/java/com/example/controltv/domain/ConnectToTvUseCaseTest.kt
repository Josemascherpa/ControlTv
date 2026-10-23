package com.example.controltv.domain

import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.session.TvSession
import com.example.controltv.domain.usecase.ConnectToTvUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectToTvUseCaseTest {

    private val credentials = FakeCredentials()
    private val lastDevice = FakeLastDevice()
    private val session = TvSession()

    private fun useCase(tv: FakeTv) = ConnectToTvUseCase({ tv }, credentials, lastDevice, session)

    @Test
    fun `conecta, deja la TV como actual y la recuerda`() = runBlocking {
        val tv = FakeTv(TvDevice("192.168.1.10", "Living", TvBrand.SAMSUNG))

        useCase(tv)(tv.device)

        assertTrue(tv.connected)
        assertSame(tv, session.controller.value)
        assertEquals(tv.device, lastDevice.device)
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
        assertNull(lastDevice.device)
    }
}
