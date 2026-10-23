package com.example.controltv.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import com.example.controltv.domain.repository.LastDeviceStore
import com.example.controltv.domain.repository.SearchHistory

// SharedPreferences es un pequeño almacenamiento clave → valor que sobrevive al cerrar la app.
// Lee desde memoria y "edit" guarda en segundo plano, por eso no hace falta usar corrutinas acá.
// Cada clase implementa una sola de las interfaces del dominio (una responsabilidad cada una).

class PrefsCredentialStore(private val prefs: SharedPreferences) : CredentialStore {
    override fun get(ip: String): String? = prefs.getString(key(ip), null)

    override fun save(ip: String, credential: String) = prefs.edit { putString(key(ip), credential) }

    override fun clear(ip: String) = prefs.edit { remove(key(ip)) }

    private fun key(ip: String) = "token_$ip"
}

class PrefsLastDeviceStore(private val prefs: SharedPreferences) : LastDeviceStore {
    override fun load(): TvDevice? {
        val ip = prefs.getString(IP, null) ?: return null
        val name = prefs.getString(NAME, null) ?: return null
        // Si guardamos una marca que ya no existe, entries.find da null en vez de fallar.
        val brand = prefs.getString(BRAND, null)?.let { saved -> TvBrand.entries.find { it.name == saved } }
            ?: return null
        return TvDevice(ip, name, brand)
    }

    override fun save(device: TvDevice) = prefs.edit {
        putString(IP, device.ip)
        putString(NAME, device.name)
        putString(BRAND, device.brand.name)
    }

    // "private companion object" = constantes de la clase que nadie más ve.
    private companion object {
        const val IP = "last_ip"
        const val NAME = "last_name"
        const val BRAND = "last_brand"
    }
}

/** Guarda las búsquedas en un solo texto, separadas por saltos de línea. */
class PrefsSearchHistory(private val prefs: SharedPreferences) : SearchHistory {
    override fun recent(): List<String> =
        prefs.getString(KEY, null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    override fun add(query: String) {
        val updated = (listOf(query) + recent().filterNot { it.equals(query, ignoreCase = true) }).take(MAX)
        prefs.edit { putString(KEY, updated.joinToString("\n")) }
    }

    private companion object {
        const val KEY = "recent_searches"
        const val MAX = 5
    }
}
