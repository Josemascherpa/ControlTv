package com.example.controltv.domain

import com.example.controltv.domain.controller.ContentSearch
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.WebBrowser
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import com.example.controltv.domain.repository.LastDeviceStore
import com.example.controltv.domain.repository.SearchHistory

// Versiones "de mentira" de las piezas del dominio: guardan en memoria lo que les pasa,
// así los tests pueden comprobar qué hizo la lógica sin red ni Android.

class FakeCredentials : CredentialStore {
    val saved = mutableMapOf<String, String>()
    override fun get(ip: String): String? = saved[ip]
    override fun save(ip: String, credential: String) { saved[ip] = credential }
    override fun clear(ip: String) { saved.remove(ip) }
}

class FakeLastDevice : LastDeviceStore {
    var device: TvDevice? = null
    override fun load(): TvDevice? = device
    override fun save(device: TvDevice) { this.device = device }
}

class FakeHistory : SearchHistory {
    val items = mutableListOf<String>()
    override fun recent(): List<String> = items
    override fun add(query: String) { items.add(0, query) }
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
