package ar.com.josemascherpa.controltv.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.com.josemascherpa.controltv.di.appContainer
import ar.com.josemascherpa.controltv.domain.controller.asPointerInput
import ar.com.josemascherpa.controltv.domain.controller.asTextInput
import ar.com.josemascherpa.controltv.domain.controller.asWebBrowser
import ar.com.josemascherpa.controltv.domain.controller.features
import ar.com.josemascherpa.controltv.domain.error.TvError
import ar.com.josemascherpa.controltv.domain.model.RemoteKey
import ar.com.josemascherpa.controltv.domain.model.ScrollDirection
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import ar.com.josemascherpa.controltv.domain.model.TvFeature
import ar.com.josemascherpa.controltv.domain.repository.SearchHistory
import ar.com.josemascherpa.controltv.domain.repository.RemotePreferences
import ar.com.josemascherpa.controltv.domain.model.FavoriteSite
import ar.com.josemascherpa.controltv.domain.session.TvSession
import ar.com.josemascherpa.controltv.domain.usecase.TurnOnTvUseCase
import ar.com.josemascherpa.controltv.domain.usecase.WebSearchUseCase
import ar.com.josemascherpa.controltv.domain.web.SearchEngine
import ar.com.josemascherpa.controltv.ui.messages.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RemoteUiState(
    /** La TV conectada, o null si no hay ninguna (entonces se muestra la pantalla de búsqueda). */
    val device: TvDevice? = null,
    val features: Set<TvFeature> = emptySet(),
    val recentSearches: List<String> = emptyList(),
    val favorites: List<FavoriteSite> = emptyList(),
    val simpleMode: Boolean = false,
    /** Estamos prendiendo la TV (tarda unos segundos): se muestra un cartel de espera. */
    val turningOn: Boolean = false,
    val message: UserMessage? = null,
)

/**
 * Lo que la pantalla del control le puede pedir al ViewModel. Es una interfaz para que la
 * pantalla no dependa del ViewModel concreto (en una vista previa se le puede pasar uno falso).
 */
interface RemoteActions {
    fun press(key: RemoteKey)
    fun sendText(text: String)
    fun sendEnter()
    fun search(query: String, engine: SearchEngine)
    fun openWebsite(input: String)
    fun openFavorite(url: String)
    fun scroll(direction: ScrollDirection)
    fun movePointer(dx: Int, dy: Int)
    fun click()
    fun disconnect()
    fun removeSearch(query: String)
    fun clearHistory()
    fun saveFavorite(originalUrl: String?, name: String, address: String)
    fun removeFavorite(url: String)
    fun moveFavorite(url: String, offset: Int)
    fun setSimpleMode(enabled: Boolean)
}

class RemoteViewModel(
    private val session: TvSession,
    private val webSearch: WebSearchUseCase,
    private val turnOnTv: TurnOnTvUseCase,
    private val history: SearchHistory,
    private val preferences: RemotePreferences,
) : ViewModel(), RemoteActions {

    private val recentSearches = MutableStateFlow(history.recent())
    private val message = MutableStateFlow<UserMessage?>(null)
    private val turningOn = MutableStateFlow(false)
    private data class Personalization(val favorites: List<FavoriteSite>, val simple: Boolean)
    private val personalization = MutableStateFlow(Personalization(preferences.favorites(), preferences.simpleMode()))

    // combine junta varios flujos en uno: cada vez que cambia cualquiera, se arma un estado nuevo.
    // stateIn lo convierte en StateFlow (siempre tiene un valor actual) atado a la vida del ViewModel.
    val state: StateFlow<RemoteUiState> =
        combine(session.controller, recentSearches, turningOn, message, personalization) { controller, recent, waking, msg, personal ->
            RemoteUiState(
                device = controller?.device,
                features = controller?.features.orEmpty(),
                recentSearches = recent,
                favorites = personal.favorites,
                simpleMode = personal.simple,
                turningOn = waking,
                message = msg,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RemoteUiState())

    override fun press(key: RemoteKey) = launchOnTv {
        if (key == RemoteKey.POWER) {
            // "finally" se ejecuta siempre, salga bien o mal: así el cartel de espera nunca queda colgado.
            try {
                turnOnTv.pressPowerButton(onTurningOn = { turningOn.value = true })
            } finally {
                turningOn.value = false
            }
        } else {
            session.current().sendKey(key)
        }
    }

    override fun sendText(text: String) {
        if (text.isBlank()) return
        launchOnTv { session.current().asTextInput().sendText(text) }
    }

    override fun sendEnter() = launchOnTv { session.current().asTextInput().sendEnter() }

    override fun search(query: String, engine: SearchEngine) = launchOnTv {
        webSearch.search(query, engine)
        recentSearches.value = history.recent()
    }

    override fun openWebsite(input: String) = launchOnTv {
        webSearch.openAddressOrSearch(input)
        recentSearches.value = history.recent()
    }

    override fun openFavorite(url: String) = launchOnTv { webSearch.openPage(url) }

    override fun scroll(direction: ScrollDirection) =
        launchOnTv { session.current().asWebBrowser().scroll(direction) }

    override fun movePointer(dx: Int, dy: Int) =
        launchOnTv { session.current().asPointerInput().movePointer(dx, dy) }

    override fun click() = launchOnTv { session.current().asPointerInput().click() }

    override fun disconnect() = session.end()

    override fun removeSearch(query: String) {
        history.remove(query)
        recentSearches.value = history.recent()
    }

    override fun clearHistory() {
        history.clear()
        recentSearches.value = history.recent()
    }

    override fun saveFavorite(originalUrl: String?, name: String, address: String) {
        val site = FavoriteSite.from(name, address) ?: return
        val sites = personalization.value.favorites.toMutableList()
        val index = sites.indexOfFirst { it.url == originalUrl }
        if (index >= 0) sites[index] = site else sites.add(site)
        updateFavorites(sites.distinctBy { it.url })
    }

    override fun removeFavorite(url: String) = updateFavorites(personalization.value.favorites.filterNot { it.url == url })

    override fun moveFavorite(url: String, offset: Int) {
        val sites = personalization.value.favorites.toMutableList()
        val index = sites.indexOfFirst { it.url == url }
        val target = index + offset
        if (index < 0 || target !in sites.indices) return
        sites.add(target, sites.removeAt(index))
        updateFavorites(sites)
    }

    private fun updateFavorites(sites: List<FavoriteSite>) {
        preferences.saveFavorites(sites)
        personalization.value = personalization.value.copy(favorites = sites)
    }

    override fun setSimpleMode(enabled: Boolean) {
        preferences.setSimpleMode(enabled)
        personalization.value = personalization.value.copy(simple = enabled)
    }

    fun messageShown() {
        message.value = null
    }

    // Ejecuta una acción sobre la TV y, si falla, muestra el aviso correspondiente.
    // Sólo atrapamos TvError: la capa "data" garantiza que es el único error que puede llegar.
    private fun launchOnTv(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: TvError) {
                message.value = UserMessage.Failure(e, session.controller.value?.device?.brand)
                if (e is TvError.ConnectionLost || e is TvError.Unreachable || e is TvError.NotConnected) {
                    session.end()
                }
            }
        }
    }

    // Se llama cuando el ViewModel se destruye (la app se cierra del todo): soltamos la TV.
    override fun onCleared() = session.end()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                RemoteViewModel(container.session, container.webSearch, container.turnOnTv, container.searchHistory, container.remotePreferences)
            }
        }
    }
}
