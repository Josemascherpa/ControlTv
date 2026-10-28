package ar.com.josemascherpa.controltv.domain.repository

import ar.com.josemascherpa.controltv.domain.model.FavoriteSite
import ar.com.josemascherpa.controltv.domain.model.KnownTv
import ar.com.josemascherpa.controltv.domain.model.ScanResult
import ar.com.josemascherpa.controltv.domain.model.TvDevice

// Interfaces para todo lo que viene "de afuera" (la red, lo guardado en el teléfono).
// La lógica depende de estas interfaces y no de cómo están hechas (la "D" de SOLID):
// así se pueden reemplazar por versiones de prueba en los tests.

/** Busca TVs en la red. */
interface TvDiscovery {
    /**
     * Recorre la red WiFi y devuelve las TVs encontradas.
     * @param knownIps IPs que se prueban sí o sí (las de "Mis TVs"), aunque estén fuera de la red recorrida.
     */
    suspend fun scan(knownIps: List<String> = emptyList()): ScanResult

    /** Prueba una IP puntual. Devuelve null si ahí no hay una TV conocida. */
    suspend fun probe(ip: String): TvDevice?
}

/** Guarda la credencial de cada TV (token, client-key o clave), para no emparejar cada vez. */
interface CredentialStore {
    fun get(ip: String): String?

    fun save(ip: String, credential: String)

    fun clear(ip: String)
}

/** "Mis TVs": las que ya se conectaron alguna vez, con el nombre que les puso la persona y su MAC. */
interface KnownTvs {
    /** Todas, la última usada primero. */
    fun all(): List<KnownTv>

    // Una interfaz puede traer funciones ya hechas: quien la implementa no necesita escribirlas.
    fun find(ip: String): KnownTv? = all().find { it.device.ip == ip }

    /** La agrega (o la actualiza si ya estaba) y la pone primera, como la última usada. */
    fun save(tv: KnownTv)

    /** Le cambia el nombre sin cambiarla de lugar. */
    fun rename(ip: String, name: String)

    fun setMacAddresses(ip: String, addresses: List<String>)

    fun remove(ip: String)
}

/** Las TVs que ya se conectaron bien alguna vez (así sabemos si hace falta explicar cómo prepararlas). */
interface PairedDevices {
    fun isPaired(ip: String): Boolean

    fun markPaired(ip: String)
}

/** Despierta TVs apagadas mandándoles el aviso "Wake-on-LAN" por la red WiFi. */
interface TvWaker {
    suspend fun wake(ip: String, macAddresses: List<String>)
}

/** Las últimas búsquedas, la más nueva primero. */
interface SearchHistory {
    fun recent(): List<String>

    fun add(query: String)

    fun remove(query: String)

    fun clear()
}

/** Preferencias personales: sobreviven al cierre de la app. El orden de favoritos se conserva. */
interface RemotePreferences {
    fun favorites(): List<FavoriteSite>
    fun saveFavorites(sites: List<FavoriteSite>)
    fun simpleMode(): Boolean
    fun setSimpleMode(enabled: Boolean)
}
