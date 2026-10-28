package ar.com.josemascherpa.controltv

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.josemascherpa.controltv.domain.model.FavoriteSite
import ar.com.josemascherpa.controltv.domain.model.RemoteKey
import ar.com.josemascherpa.controltv.domain.model.ScrollDirection
import ar.com.josemascherpa.controltv.domain.model.TvBrand
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import ar.com.josemascherpa.controltv.domain.model.TvFeature
import ar.com.josemascherpa.controltv.domain.web.SearchEngine
import ar.com.josemascherpa.controltv.ui.remote.RemoteActions
import ar.com.josemascherpa.controltv.ui.remote.RemoteScreen
import ar.com.josemascherpa.controltv.ui.remote.RemoteUiState
import ar.com.josemascherpa.controltv.ui.theme.ControlTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class RemoteScreenTest {
    @get:Rule val compose = createComposeRule()
    private val actions = DemoActions()

    private fun show(simple: Boolean = false, features: Set<TvFeature> = TvFeature.entries.toSet()) {
        actions.state = actions.state.copy(simpleMode = simple, features = features)
        compose.setContent {
            ControlTvTheme(darkTheme = false) {
                Surface { RemoteScreen(requireNotNull(actions.state.device), actions.state, actions) }
            }
        }
    }

    @Test fun volumeAndPowerStayVisibleInInternetAndPointer() {
        show()
        screenshot("control.png")
        compose.onNodeWithText("Internet").performClick()
        compose.onNodeWithText("Volumen +").assertIsDisplayed().performClick()
        assertEquals(RemoteKey.VOLUME_UP, actions.lastKey)
        compose.onNodeWithText("Mover el puntero con el dedo").performScrollTo().performClick()
        compose.onNodeWithText("Prender / Apagar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pad para mover el mouse de la TV").assertIsDisplayed()
        screenshot("puntero.png")
    }

    @Test fun simpleModeShowsVoiceSearchAndCanBeDisabled() {
        show(simple = true)
        compose.onNodeWithContentDescription("Buscar hablando").assertIsDisplayed()
        compose.onNodeWithText("Internet").assertDoesNotExist()
        screenshot("simple.png")
        compose.onNodeWithText("Ver todas las funciones").performClick()
        compose.onNodeWithText("Internet").assertIsDisplayed()
    }

    @Test fun favoritesValidateInputAndCanBeAddedFromTheScreen() {
        show()
        compose.onNodeWithText("Internet").performClick()
        compose.onNodeWithText("Editar páginas favoritas").performScrollTo().performClick()
        compose.onNodeWithText("Agregar página").performScrollTo().performClick()
        compose.onNodeWithText("Nombre").performTextInput("Mi página")
        compose.onNodeWithText("Dirección, por ejemplo wikipedia.org").performTextInput("example.com")
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("Mi página").performScrollTo().assertIsDisplayed()
        assertEquals("https://example.com", actions.state.favorites.last().url)
    }

    @Test fun removesIndividualHistoryWithoutOpeningIt() {
        show(features = setOf(TvFeature.CONTENT_SEARCH))
        compose.onNodeWithText("Internet").performClick()
        compose.onNodeWithContentDescription("Borrar búsqueda: Clima").performScrollTo().performClick()
        compose.onNodeWithText("Clima").assertDoesNotExist()
        assertEquals(listOf("Noticias"), actions.state.recentSearches)
        compose.onNodeWithText("Borrar todas").performScrollTo().performClick()
        compose.onNodeWithText("Últimas búsquedas").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), name)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** Dispositivo simulado sólo para probar la UI; no abre conexiones de red. */
private class DemoActions : RemoteActions {
    var state by mutableStateOf(RemoteUiState(
        device = TvDevice("192.168.1.10", "TV del living", TvBrand.LG),
        favorites = listOf(FavoriteSite("Google", "https://www.google.com")),
        recentSearches = listOf("Clima", "Noticias"),
    ))
    var lastKey: RemoteKey? = null
    override fun press(key: RemoteKey) { lastKey = key }
    override fun setSimpleMode(enabled: Boolean) { state = state.copy(simpleMode = enabled) }
    override fun removeSearch(query: String) { state = state.copy(recentSearches = state.recentSearches - query) }
    override fun clearHistory() { state = state.copy(recentSearches = emptyList()) }
    override fun saveFavorite(originalUrl: String?, name: String, address: String) {
        state = state.copy(favorites = state.favorites + listOfNotNull(FavoriteSite.from(name, address)))
    }
    override fun removeFavorite(url: String) {}
    override fun moveFavorite(url: String, offset: Int) {}
    override fun sendText(text: String) {}
    override fun sendEnter() {}
    override fun search(query: String, engine: SearchEngine) {}
    override fun openWebsite(input: String) {}
    override fun openFavorite(url: String) {}
    override fun scroll(direction: ScrollDirection) {}
    override fun movePointer(dx: Int, dy: Int) {}
    override fun click() {}
    override fun disconnect() {}
}
