package com.example.controltv.domain

import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.KnownTv
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.session.TvSession
import com.example.controltv.domain.usecase.ConnectToTvUseCase
import com.example.controltv.domain.usecase.TurnOnTvUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnOnTvUseCaseTest {

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
