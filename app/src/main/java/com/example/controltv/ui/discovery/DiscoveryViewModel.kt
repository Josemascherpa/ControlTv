package com.example.controltv.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.controltv.di.appContainer
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.PairingMethod
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

/** Por qué se abre la guía para activar la depuración (cambia el título y la explicación). */
enum class SetupGuideReason {
    /** Es la primera vez que se conecta esta TV: explicamos antes de intentar. */
    FIRST_TIME,

    /** Se intentó conectar y la TV no aceptó: explicamos qué revisar. */
    FAILED,

    /** La persona no encuentra su TV en la lista (con la depuración apagada no aparece). */
    NOT_FOUND,
}

/** @param device la TV a conectar al terminar la guía, o null si todavía no aparece. */
data class SetupGuideRequest(val device: TvDevice?, val reason: SetupGuideReason)

/**
 * La TV está mostrando un código y esperamos que la persona lo escriba (Google TV).
 * @param wrongCode el último código escrito no coincidía.
 * @param checking ya lo mandamos y estamos esperando la respuesta de la TV.
 */
data class CodeRequest(val device: TvDevice, val wrongCode: Boolean = false, val checking: Boolean = false)

/** Todo lo que la pantalla de búsqueda necesita para dibujarse, en un solo objeto inmutable. */
data class DiscoveryUiState(
    val scanning: Boolean = false,
    val devices: List<TvDevice> = emptyList(),
    val lastDevice: TvDevice? = null,
    /** TV con la que se está conectando ahora (muestra el cartel de "Conectando…"). */
    val connecting: TvDevice? = null,
    /** TV que necesita clave y todavía no la tenemos (muestra el cartel para escribirla). */
    val keyRequest: TvDevice? = null,
    /** TV que está mostrando un código para escribir en el teléfono. */
    val codeRequest: CodeRequest? = null,
    /** Guía para activar la depuración (Google TV / Fire TV), si hay que mostrarla. */
    val setupGuide: SetupGuideRequest? = null,
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
        // Si es una Google TV / Fire TV nueva, primero explicamos cómo prepararla.
        if (connectToTv.needsSetup(device)) {
            _state.update { it.copy(setupGuide = SetupGuideRequest(device, SetupGuideReason.FIRST_TIME)) }
        } else {
            startConnection(device, key)
        }
    }

    /** Abre la guía desde el botón "¿No aparece tu TV?". */
    fun showSetupGuide() =
        _state.update { it.copy(setupGuide = SetupGuideRequest(device = null, SetupGuideReason.NOT_FOUND)) }

    /** La persona tocó "Ya lo activé": conectamos la TV o, si no aparecía, buscamos de nuevo. */
    fun confirmSetupGuide() {
        val request = _state.value.setupGuide ?: return
        _state.update { it.copy(setupGuide = null) }
        if (request.device != null) startConnection(request.device, key = null) else scan()
    }

    fun dismissSetupGuide() = _state.update { it.copy(setupGuide = null) }

    private fun startConnection(device: TvDevice, key: String?) {
        viewModelScope.launch {
            _state.update { it.copy(connecting = device, keyRequest = null) }
            try {
                connectToTv(device, key)
                _state.update { it.copy(connecting = null, lastDevice = device) }
            } catch (e: TvError.MissingKey) {
                _state.update { it.copy(connecting = null, keyRequest = device) }
            } catch (e: TvError.PairingCodeRequired) {
                _state.update { it.copy(connecting = null, codeRequest = CodeRequest(device)) }
            } catch (e: TvError) {
                showFailure(device, e)
            }
        }
    }

    /** La persona escribió el código que muestra la TV. */
    fun submitPairingCode(code: String) {
        val request = _state.value.codeRequest ?: return
        viewModelScope.launch {
            _state.update { it.copy(codeRequest = request.copy(checking = true)) }
            try {
                connectToTv.submitPairingCode(code)
                _state.update { it.copy(codeRequest = null, lastDevice = request.device) }
            } catch (e: TvError.WrongPairingCode) {
                // Dejamos el cartel abierto para que lo vuelva a escribir.
                _state.update { it.copy(codeRequest = request.copy(wrongCode = true, checking = false)) }
            } catch (e: TvError) {
                _state.update { it.copy(codeRequest = null) }
                showFailure(request.device, e)
            }
        }
    }

    fun cancelPairingCode() {
        connectToTv.cancelPairing()
        _state.update { it.copy(codeRequest = null) }
    }

    private fun showFailure(device: TvDevice, error: TvError) = _state.update {
        if (needsSetupHelp(device, error)) {
            it.copy(connecting = null, setupGuide = SetupGuideRequest(device, SetupGuideReason.FAILED))
        } else {
            it.copy(connecting = null, message = UserMessage.Failure(error, device.brand.pairing))
        }
    }

    // Si una TV con depuración no aceptó la conexión, en vez de un aviso corto mostramos la guía completa.
    private fun needsSetupHelp(device: TvDevice, error: TvError): Boolean =
        device.brand.pairing == PairingMethod.ALLOW_DEBUGGING &&
            (error is TvError.PairingRejected || error is TvError.PairingTimeout)

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
