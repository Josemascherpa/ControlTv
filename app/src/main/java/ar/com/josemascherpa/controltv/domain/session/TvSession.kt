package ar.com.josemascherpa.controltv.domain.session

import ar.com.josemascherpa.controltv.domain.controller.TvController
import ar.com.josemascherpa.controltv.domain.error.TvError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La TV conectada en este momento. Hay una sola para toda la app: la pantalla de búsqueda
 * la conecta y la del control remoto la usa, sin tener que conocerse entre ellas.
 */
class TvSession {
    // MutableStateFlow = valor observable que podemos modificar desde acá.
    // Hacia afuera lo exponemos como StateFlow (sólo lectura).
    private val _controller = MutableStateFlow<TvController?>(null)
    val controller: StateFlow<TvController?> = _controller.asStateFlow()

    fun start(controller: TvController) {
        _controller.value?.disconnect()
        _controller.value = controller
    }

    fun end() {
        _controller.value?.disconnect()
        _controller.value = null
    }

    /** El controlador actual, o [TvError.NotConnected] si no hay ninguna TV conectada. */
    fun current(): TvController = _controller.value ?: throw TvError.NotConnected()
}
