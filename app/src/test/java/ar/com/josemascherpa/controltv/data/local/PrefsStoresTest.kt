package ar.com.josemascherpa.controltv.data.local

import ar.com.josemascherpa.controltv.domain.model.FavoriteSite
import ar.com.josemascherpa.controltv.domain.model.KnownTv
import ar.com.josemascherpa.controltv.domain.model.TvBrand
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import org.junit.Assert.*
import org.junit.Test

class PrefsStoresTest {
    private val prefs = MemoryPreferences()

    @Test fun `favoritos conservan cambios orden y lista vacia al recrear el repositorio`() {
        val first = PrefsRemotePreferences(prefs)
        assertEquals(FavoriteSite.defaults, first.favorites())
        val custom = listOf(FavoriteSite("Wiki", "https://es.wikipedia.org"), FavoriteSite("Videos", "https://youtube.com"))
        first.saveFavorites(custom)
        assertEquals(custom, PrefsRemotePreferences(prefs).favorites())
        first.saveFavorites(emptyList())
        assertTrue(PrefsRemotePreferences(prefs).favorites().isEmpty())
    }

    @Test fun `modo simple se recuerda y datos dañados recuperan favoritos iniciales`() {
        PrefsRemotePreferences(prefs).setSimpleMode(true)
        assertTrue(PrefsRemotePreferences(prefs).simpleMode())
        prefs.edit().putString("favorite_sites", "roto").apply()
        assertEquals(FavoriteSite.defaults, PrefsRemotePreferences(prefs).favorites())
    }

    @Test fun `historial permite borrar una busqueda o todas sin reaparecer`() {
        val history = PrefsSearchHistory(prefs)
        history.add("Clima")
        history.add("Noticias")
        history.remove("CLIMA")
        assertEquals(listOf("Noticias"), PrefsSearchHistory(prefs).recent())
        history.clear()
        assertTrue(PrefsSearchHistory(prefs).recent().isEmpty())
    }

    @Test fun `editar MAC conserva nombre y orden de las TVs`() {
        val store = PrefsKnownTvs(prefs)
        val living = TvDevice("192.168.1.10", "Living", TvBrand.LG)
        val room = TvDevice("192.168.1.20", "Pieza", TvBrand.SAMSUNG)
        store.save(KnownTv(living))
        store.save(KnownTv(room))
        store.setMacAddresses(living.ip, listOf("A4:30:7A:12:34:56"))
        val restored = PrefsKnownTvs(prefs)
        assertEquals(listOf(room, living), restored.all().map { it.device })
        assertTrue(requireNotNull(restored.find(living.ip)).canWakeUp)
    }
}
