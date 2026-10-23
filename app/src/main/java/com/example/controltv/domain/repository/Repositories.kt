package com.example.controltv.domain.repository

import com.example.controltv.domain.model.TvDevice

// Interfaces para todo lo que viene "de afuera" (la red, lo guardado en el teléfono).
// La lógica depende de estas interfaces y no de cómo están hechas (la "D" de SOLID):
// así se pueden reemplazar por versiones de prueba en los tests.

/** Busca TVs en la red. */
interface TvDiscovery {
    /** Recorre la red WiFi y devuelve las TVs encontradas (vacía si no hay ninguna). */
    suspend fun scan(): List<TvDevice>

    /** Prueba una IP puntual. Devuelve null si ahí no hay una TV conocida. */
    suspend fun probe(ip: String): TvDevice?
}

/** Guarda la credencial de cada TV (token, client-key o clave), para no emparejar cada vez. */
interface CredentialStore {
    fun get(ip: String): String?

    fun save(ip: String, credential: String)

    fun clear(ip: String)
}

/** La última TV usada, para ofrecer reconectar con un toque. */
interface LastDeviceStore {
    fun load(): TvDevice?

    fun save(device: TvDevice)
}

/** Las TVs que ya se conectaron bien alguna vez (así sabemos si hace falta explicar cómo prepararlas). */
interface PairedDevices {
    fun isPaired(ip: String): Boolean

    fun markPaired(ip: String)
}

/** Las últimas búsquedas, la más nueva primero. */
interface SearchHistory {
    fun recent(): List<String>

    fun add(query: String)
}
