package com.example.controltv.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.controltv.domain.model.KnownTv
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import com.example.controltv.domain.repository.KnownTvs
import com.example.controltv.domain.repository.PairedDevices
import com.example.controltv.domain.repository.SearchHistory
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// SharedPreferences es un pequeño almacenamiento clave → valor que sobrevive al cerrar la app.
// Lee desde memoria y "edit" guarda en segundo plano, por eso no hace falta usar corrutinas acá.
// Cada clase implementa una sola de las interfaces del dominio (una responsabilidad cada una).

class PrefsCredentialStore(private val prefs: SharedPreferences) : CredentialStore {
    override fun get(ip: String): String? = prefs.getString(key(ip), null)

    override fun save(ip: String, credential: String) = prefs.edit { putString(key(ip), credential) }

    override fun clear(ip: String) = prefs.edit { remove(key(ip)) }

    private fun key(ip: String) = "token_$ip"
}

/**
 * "Mis TVs", guardadas como un texto JSON: [{"ip":…, "name":…, "brand":…, "macs":[…]}, …].
 * JSON es un formato de texto estándar para datos; Android trae JSONArray/JSONObject para leerlo.
 */
class PrefsKnownTvs(private val prefs: SharedPreferences) : KnownTvs {

    override fun all(): List<KnownTv> {
        val json = prefs.getString(KEY, null) ?: return listOfNotNull(oldLastDevice())
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { read(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList() // sólo pasaría si se rompe lo guardado: arrancamos con la lista vacía
        }
    }

    override fun save(tv: KnownTv) = write(listOf(tv) + all().filterNot { it.device.ip == tv.device.ip })

    override fun rename(ip: String, name: String) = write(
        all().map { if (it.device.ip == ip) it.copy(device = it.device.copy(name = name)) else it },
    )

    override fun remove(ip: String) = write(all().filterNot { it.device.ip == ip })

    private fun write(tvs: List<KnownTv>) = prefs.edit {
        putString(KEY, JSONArray(tvs.map(::toJson)).toString())
    }

    private fun toJson(tv: KnownTv): JSONObject = JSONObject()
        .put(IP, tv.device.ip)
        .put(NAME, tv.device.name)
        .put(BRAND, tv.device.brand.name)
        .put(MACS, JSONArray(tv.macAddresses))

    // Si guardamos una marca que ya no existe, entries.find da null y esa TV se saltea.
    private fun read(json: JSONObject): KnownTv? {
        val brand = TvBrand.entries.find { it.name == json.optString(BRAND) } ?: return null
        val macs = json.optJSONArray(MACS) ?: JSONArray()
        return KnownTv(
            device = TvDevice(json.getString(IP), json.getString(NAME), brand),
            macAddresses = (0 until macs.length()).map { macs.getString(it) },
        )
    }

    // Las versiones anteriores de la app guardaban sólo la última TV (y aparte su MAC).
    // La pasamos a la lista nueva para que la persona no la pierda.
    private fun oldLastDevice(): KnownTv? {
        val ip = prefs.getString(OLD_IP, null) ?: return null
        val name = prefs.getString(OLD_NAME, null) ?: return null
        val brand = prefs.getString(OLD_BRAND, null)?.let { saved -> TvBrand.entries.find { it.name == saved } }
            ?: return null
        val macs = prefs.getString("mac_$ip", null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        return KnownTv(TvDevice(ip, name, brand), macs)
    }

    // "private companion object" = constantes de la clase que nadie más ve.
    private companion object {
        const val KEY = "known_tvs"
        const val IP = "ip"
        const val NAME = "name"
        const val BRAND = "brand"
        const val MACS = "macs"
        const val OLD_IP = "last_ip"
        const val OLD_NAME = "last_name"
        const val OLD_BRAND = "last_brand"
    }
}

/** Guarda las IPs en un "set" (conjunto sin repetidos). */
class PrefsPairedDevices(private val prefs: SharedPreferences) : PairedDevices {
    override fun isPaired(ip: String): Boolean = ip in paired()

    // getStringSet devuelve un set que Android no deja modificar: armamos uno nuevo con "+".
    override fun markPaired(ip: String) = prefs.edit { putStringSet(KEY, paired() + ip) }

    private fun paired(): Set<String> = prefs.getStringSet(KEY, null).orEmpty()

    private companion object {
        const val KEY = "paired_ips"
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
