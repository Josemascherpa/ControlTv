package com.example.controltv.domain

import com.example.controltv.domain.controller.CodePairing
import com.example.controltv.domain.controller.ContentSearch
import com.example.controltv.domain.controller.HardwareAddress
import com.example.controltv.domain.controller.PowerOn
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.WebBrowser
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import com.example.controltv.domain.model.KnownTv
import com.example.controltv.domain.repository.KnownTvs
import com.example.controltv.domain.repository.PairedDevices
import com.example.controltv.domain.repository.SearchHistory
import com.example.controltv.domain.repository.TvWaker

// Versiones "de mentira" de las piezas del dominio: guardan en memoria lo que les pasa,
// así los tests pueden comprobar qué hizo la lógica sin red ni Android.

class FakeCredentials : CredentialStore {
    val saved = mutableMapOf<String, String>()
    override fun get(ip: String): String? = saved[ip]
    override fun save(ip: String, credential: String) { saved[ip] = credential }
    override fun clear(ip: String) { saved.remove(ip) }
}

class FakeKnownTvs : KnownTvs {
    val tvs = mutableListOf<KnownTv>()
    override fun all(): List<KnownTv> = tvs.toList()
    override fun save(tv: KnownTv) {
        tvs.removeAll { it.device.ip == tv.device.ip }
        tvs.add(0, tv)
    }
    override fun rename(ip: String, name: String) {
        tvs.replaceAll { if (it.device.ip == ip) it.copy(device = it.device.copy(name = name)) else it }
    }
    override fun remove(ip: String) { tvs.removeAll { it.device.ip == ip } }
}

class FakePaired : PairedDevices {
    val ips = mutableSetOf<String>()
    override fun isPaired(ip: String): Boolean = ip in ips
    override fun markPaired(ip: String) { ips.add(ip) }
}

class FakeHistory : SearchHistory {
    val items = mutableListOf<String>()
    override fun recent(): List<String> = items
    override fun add(query: String) { items.add(0, query) }
}

/** Anota a qué MAC se mandó el aviso de encendido. Si hay una [tv], la "prende" al recibirlo. */
class FakeWaker(private val tv: FakeOffTv? = null) : TvWaker {
    val woken = mutableListOf<List<String>>()
    override suspend fun wake(ip: String, macAddresses: List<String>) {
        woken.add(macAddresses)
        tv?.off = false
    }
}

/** TV básica: sólo botones. */
open class FakeTv(override val device: TvDevice, private val failOnConnect: Throwable? = null) : TvController {
    var connected = false
    var disconnected = false
    override suspend fun connect() {
        failOnConnect?.let { throw it }
        connected = true
    }
    override suspend fun sendKey(key: RemoteKey) {}
    override fun disconnect() { disconnected = true }
}

/** TV con navegador. */
class FakeBrowserTv(device: TvDevice) : FakeTv(device), WebBrowser {
    val openedUrls = mutableListOf<String>()
    override suspend fun openUrl(url: String) { openedUrls.add(url) }
    override suspend fun scroll(direction: ScrollDirection) {}
}

/** TV sin navegador pero con buscador propio (como Roku). */
class FakeSearchTv(device: TvDevice) : FakeTv(device), ContentSearch {
    val searches = mutableListOf<String>()
    override suspend fun search(query: String) { searches.add(query) }
}

/** TV que pide un código la primera vez (como Google TV). El código correcto es [rightCode]. */
class FakeCodeTv(device: TvDevice, private val rightCode: String) : FakeTv(device), CodePairing {
    private var paired = false
    override suspend fun connect() {
        if (!paired) throw TvError.PairingCodeRequired()
        super.connect()
    }
    override suspend fun finishPairing(code: String) {
        if (code != rightCode) throw TvError.WrongPairingCode()
        paired = true
    }
}

/**
 * TV que se puede apagar: apagada no contesta (como una de verdad sin WiFi).
 * Sabe su MAC y se puede prender con su protocolo ([PowerOn]) mientras está en espera.
 */
class FakeOffTv(device: TvDevice, var off: Boolean = false) : FakeTv(device), HardwareAddress, PowerOn {
    val pressed = mutableListOf<RemoteKey>()
    var turnedOn = false
    override suspend fun connect() {
        if (off) throw TvError.Unreachable()
        super.connect()
    }
    override suspend fun sendKey(key: RemoteKey) {
        if (off) throw TvError.ConnectionLost()
        pressed.add(key)
    }
    override suspend fun macAddresses(): List<String> = listOf(MAC)
    override suspend fun turnOn() { turnedOn = true }

    companion object {
        const val MAC = "A4:30:7A:12:34:56"
    }
}
