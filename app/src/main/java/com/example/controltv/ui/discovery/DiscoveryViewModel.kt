package com.example.controltv.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.controltv.di.appContainer
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.LastDeviceStore
import com.example.controltv.domain.repository.TvDiscovery
import com.example.controltv.domain.usecase.ConnectToTvUseCase
import com.example.controltv.ui.messages.UserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Todo lo que la pantalla de búsqueda necesita para dibujarse, en un solo objeto inmutable. */
data class DiscoveryUiState(
    val scanning: Boolean = false,
    val devices: List<TvDevice> = emptyList(),
    val lastDevice: TvDevice? = null,
    /** TV con la que se está conectando ahora (muestra el cartel de "Conectando…"). */
    val connecting: TvDevice? = null,
    /** TV que necesita clave y todavía no la tenemos (muestra el cartel para escribirla). */
    val keyRequest: TvDevice? = null,
    val message: UserMessage? = null,
)

/**
 * El ViewModel guarda el estado y la lógica de la pantalla, separado de la UI.
 * La UI observa [state] y se redibuja sola cuando cambia (eso es Compose).
 * Sólo conoce interfaces y casos de uso del dominio, nunca las clases de "data".
 */
class DiscoveryViewModel(
    private val discovery: TvDiscovery,
    private val connectToTv: ConnectToTvUseCase,
    lastDevice: LastDeviceStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoveryUiState(lastDevice = lastDevice.load()))
    val state: StateFlow<DiscoveryUiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        // viewModelScope lanza una corrutina que se cancela sola si se destruye el ViewModel.
        viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            val found = discovery.scan()
            _state.update {
                it.copy(
                    scanning = false,
                    devices = found,
                    message = if (found.isEmpty()) UserMessage.NoTvsFound else null,
                )
            }
        }
    }

    fun connectManual(ip: String) {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val device = discovery.probe(trimmed)
            if (device == null) {
                _state.update { it.copy(message = UserMessage.NoTvAtIp(trimmed)) }
            } else {
                connect(device)
            }
        }
    }

    /** @param key la clave que escribió la persona, si la TV la pide. */
    fun connect(device: TvDevice, key: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(connecting = device, keyRequest = null) }
            try {
                connectToTv(device, key)
                _state.update { it.copy(connecting = null, lastDevice = device) }
            } catch (e: TvError.MissingKey) {
                _state.update { it.copy(connecting = null, keyRequest = device) }
            } catch (e: TvError) {
                _state.update {
                    it.copy(connecting = null, message = UserMessage.Failure(e, device.brand.pairing))
                }
            }
        }
    }

    fun cancelKeyRequest() = _state.update { it.copy(keyRequest = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    companion object {
        // Le dice a Android cómo crear este ViewModel con sus dependencias.
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                DiscoveryViewModel(container.discovery, container.connectToTv, container.lastDevice)
            }
        }
    }
}
