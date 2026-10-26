package com.example.controltv.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.controltv.di.appContainer
import com.example.controltv.domain.controller.asPointerInput
import com.example.controltv.domain.controller.asTextInput
import com.example.controltv.domain.controller.asWebBrowser
import com.example.controltv.domain.controller.features
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.model.TvFeature
import com.example.controltv.domain.repository.SearchHistory
import com.example.controltv.domain.session.TvSession
import com.example.controltv.domain.usecase.TurnOnTvUseCase
import com.example.controltv.domain.usecase.WebSearchUseCase
import com.example.controltv.domain.web.SearchEngine
import com.example.controltv.ui.messages.UserMessage
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
}

class RemoteViewModel(
    private val session: TvSession,
    private val webSearch: WebSearchUseCase,
    private val turnOnTv: TurnOnTvUseCase,
    private val history: SearchHistory,
) : ViewModel(), RemoteActions {

    private val recentSearches = MutableStateFlow(history.recent())
    private val message = MutableStateFlow<UserMessage?>(null)
    private val turningOn = MutableStateFlow(false)

    // combine junta varios flujos en uno: cada vez que cambia cualquiera, se arma un estado nuevo.
    // stateIn lo convierte en StateFlow (siempre tiene un valor actual) atado a la vida del ViewModel.
    val state: StateFlow<RemoteUiState> =
        combine(session.controller, recentSearches, turningOn, message) { controller, recent, waking, msg ->
            RemoteUiState(
                device = controller?.device,
                features = controller?.features.orEmpty(),
                recentSearches = recent,
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
            }
        }
    }

    // Se llama cuando el ViewModel se destruye (la app se cierra del todo): soltamos la TV.
    override fun onCleared() = session.end()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                RemoteViewModel(container.session, container.webSearch, container.turnOnTv, container.searchHistory)
            }
        }
    }
}
