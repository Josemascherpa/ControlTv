package ar.com.josemascherpa.controltv.ui.remote

import ar.com.josemascherpa.controltv.data.local.MemoryPreferences
import ar.com.josemascherpa.controltv.data.local.PrefsRemotePreferences
import ar.com.josemascherpa.controltv.domain.FakeCredentials
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import ar.com.josemascherpa.controltv.domain.model.TvBrand
import ar.com.josemascherpa.controltv.domain.model.RemoteKey
import ar.com.josemascherpa.controltv.domain.error.TvError
import ar.com.josemascherpa.controltv.domain.FakeHistory
import ar.com.josemascherpa.controltv.domain.FakeKnownTvs
import ar.com.josemascherpa.controltv.domain.FakePaired
import ar.com.josemascherpa.controltv.domain.FakeTv
import ar.com.josemascherpa.controltv.domain.FakeWaker
import ar.com.josemascherpa.controltv.domain.session.TvSession
import ar.com.josemascherpa.controltv.domain.usecase.ConnectToTvUseCase
import ar.com.josemascherpa.controltv.domain.usecase.TurnOnTvUseCase
import ar.com.josemascherpa.controltv.domain.usecase.WebSearchUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModelTest {
    private val preferences = PrefsRemotePreferences(MemoryPreferences())
    private val history = FakeHistory()
    private lateinit var model: RemoteViewModel
    private lateinit var session: TvSession

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        session = TvSession()
        val tvs = FakeKnownTvs()
        val connect = ConnectToTvUseCase({ FakeTv(it) }, FakeCredentials(), tvs, FakePaired(), session)
        model = RemoteViewModel(session, WebSearchUseCase(session, history), TurnOnTvUseCase(FakeWaker(), tvs, connect, session), history, preferences)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `cortar la conexion termina la sesion que mantiene el servicio activo`() {
        val device = TvDevice("192.168.1.10", "Living", TvBrand.LG)
        session.start(object : FakeTv(device) {
            override suspend fun sendKey(key: RemoteKey) { throw TvError.ConnectionLost() }
        })
        model.press(RemoteKey.HOME)
        assertNull(session.controller.value)
        assertNull(model.state.value.device)
        assertNotNull(model.state.value.message)
    }

    @Test fun `un boton no soportado conserva la conexion`() {
        val device = TvDevice("192.168.1.10", "Living", TvBrand.LG)
        val tv = object : FakeTv(device) {
            override suspend fun sendKey(key: RemoteKey) { throw TvError.KeyNotSupported(key) }
        }
        session.start(tv)
        model.press(RemoteKey.MENU)
        assertSame(tv, session.controller.value)
        assertNotNull(model.state.value.message)
    }

    @Test fun `agrega edita mueve y elimina favoritos con estado y persistencia coherentes`() {
        model.saveFavorite(null, "Nueva", "example.com")
        val url = "https://example.com"
        assertEquals("Nueva", model.state.value.favorites.last().name)
        model.saveFavorite(url, "Editada", "example.com/noticias")
        model.moveFavorite("https://example.com/noticias", -1)
        assertEquals("Editada", model.state.value.favorites[model.state.value.favorites.lastIndex - 1].name)
        model.removeFavorite("https://example.com/noticias")
        assertEquals(preferences.favorites(), model.state.value.favorites)
        assertFalse(model.state.value.favorites.any { it.name == "Editada" })
    }

    @Test fun `valida favoritos y limita movimientos fuera de lista`() {
        val original = model.state.value.favorites
        model.saveFavorite(null, "", "https://example.com")
        model.moveFavorite(original.first().url, -1)
        model.moveFavorite("desconocida", 1)
        assertEquals(original, model.state.value.favorites)
    }

    @Test fun `modo simple y borrado de historial actualizan estado`() {
        model.setSimpleMode(true)
        assertTrue(model.state.value.simpleMode)
        assertTrue(preferences.simpleMode())
        history.add("Clima")
        history.add("Noticias")
        model.removeSearch("Clima")
        assertEquals(listOf("Noticias"), model.state.value.recentSearches)
        model.clearHistory()
        assertTrue(model.state.value.recentSearches.isEmpty())
    }
}
