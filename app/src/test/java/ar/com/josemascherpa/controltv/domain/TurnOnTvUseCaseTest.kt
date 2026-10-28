package ar.com.josemascherpa.controltv.domain

import ar.com.josemascherpa.controltv.domain.error.TvError
import ar.com.josemascherpa.controltv.domain.model.KnownTv
import ar.com.josemascherpa.controltv.domain.model.RemoteKey
import ar.com.josemascherpa.controltv.domain.model.TvBrand
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import ar.com.josemascherpa.controltv.domain.session.TvSession
import ar.com.josemascherpa.controltv.domain.usecase.ConnectToTvUseCase
import ar.com.josemascherpa.controltv.domain.usecase.TurnOnTvUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnOnTvUseCaseTest {

    @Test fun `si falla UDP todavia intenta encendido con el protocolo de la TV`() = runBlocking {
        val tv = FakeOffTv(device)
        knownTvs.save(KnownTv(device, listOf(FakeOffTv.MAC)))
        val connect = ConnectToTvUseCase({ tv }, FakeCredentials(), knownTvs, FakePaired(), session)
        val blocked = object : ar.com.josemascherpa.controltv.domain.repository.TvWaker {
            override suspend fun wake(ip: String, macAddresses: List<String>) { throw TvError.Unreachable() }
        }
        TurnOnTvUseCase(blocked, knownTvs, connect, session, pause = {})(device)
        assertTrue(tv.turnedOn)
    }

    @Test fun `no reintenta errores de emparejamiento al prender`() = runBlocking {
        val tv = FakeCodeTv(device, "123456")
        knownTvs.save(KnownTv(device, listOf(FakeOffTv.MAC)))
        val waker = FakeWaker()
        val error = runCatching { useCases(tv, waker).second(device) }.exceptionOrNull()
        assertTrue(error is TvError.PairingCodeRequired)
        assertEquals(1, waker.woken.size)
    }

    private val device = TvDevice("192.168.1.10", "Living", TvBrand.LG)
    private val knownTvs = FakeKnownTvs()
    private val session = TvSession()

    private fun useCases(tv: FakeTv, waker: FakeWaker): Pair<ConnectToTvUseCase, TurnOnTvUseCase> {
        val connect = ConnectToTvUseCase({ tv }, FakeCredentials(), knownTvs, FakePaired(), session)
        return connect to TurnOnTvUseCase(waker, knownTvs, connect, session, pause = {})
    }

    @Test
    fun `al conectar una TV prendida guarda su MAC`() = runBlocking {
        val tv = FakeOffTv(device)
        val (connect, _) = useCases(tv, FakeWaker())

        connect(device)

        assertEquals(listOf(FakeOffTv.MAC), knownTvs.find(device.ip)?.macAddresses)
    }

    @Test
    fun `una TV apagada con MAC conocida se prende y queda conectada`() = runBlocking {
        val tv = FakeOffTv(device, off = true)
        val waker = FakeWaker(tv)
        knownTvs.save(KnownTv(device, listOf(FakeOffTv.MAC)))

        useCases(tv, waker).second(device)

        assertEquals(listOf(listOf(FakeOffTv.MAC)), waker.woken)
        assertSame(tv, session.controller.value)
        assertTrue(tv.turnedOn)
    }

    @Test(expected = TvError.CantTurnOn::class)
    fun `sin MAC y sin contestar no se puede prender`(): Unit = runBlocking {
        val tv = FakeOffTv(device, off = true)
        useCases(tv, FakeWaker(tv)).second(device)
    }

    @Test
    fun `el boton de encendido apaga una TV que contesta`() = runBlocking {
        val tv = FakeOffTv(device)
        val waker = FakeWaker(tv)
        val (connect, turnOn) = useCases(tv, waker)
        connect(device)

        turnOn.pressPowerButton()

        assertEquals(listOf(RemoteKey.POWER), tv.pressed)
        assertTrue(waker.woken.isEmpty())
    }

    @Test
    fun `el boton de encendido prende una TV que se apago`() = runBlocking {
        val tv = FakeOffTv(device)
        val waker = FakeWaker(tv)
        val (connect, turnOn) = useCases(tv, waker)
        connect(device) // estaba prendida: aprendimos su MAC
        tv.off = true

        var turningOn = false
        turnOn.pressPowerButton(onTurningOn = { turningOn = true })

        assertTrue(turningOn)
        assertEquals(listOf(listOf(FakeOffTv.MAC)), waker.woken)
        assertTrue(tv.turnedOn)
    }
}
