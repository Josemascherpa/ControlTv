package com.example.controltv.domain.usecase

import com.example.controltv.domain.controller.TvControllerFactory
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.PairingMethod
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import com.example.controltv.domain.repository.LastDeviceStore
import com.example.controltv.domain.session.TvSession

/**
 * Un "caso de uso" es una acción concreta de la app, con su lógica en un solo lugar.
 * Este conecta una TV: guarda la clave si la pasaron, crea el controlador, empareja
 * y la deja como TV actual.
 *
 * Las dependencias llegan por el constructor (no las crea él), así en un test se le pueden
 * pasar versiones de prueba.
 */
class ConnectToTvUseCase(
    private val controllerFactory: TvControllerFactory,
    private val credentials: CredentialStore,
    private val lastDevice: LastDeviceStore,
    private val session: TvSession,
) {
    /**
     * "operator fun invoke" permite llamarlo como si fuera una función: connectToTv(device).
     * @param key la clave que escribió la persona, para las TVs que la piden (Sony).
     * @throws TvError.MissingKey si la TV necesita clave y todavía no la tenemos.
     */
    suspend operator fun invoke(device: TvDevice, key: String? = null) {
        key?.trim()?.takeIf { it.isNotEmpty() }?.let { credentials.save(device.ip, it) }
        if (device.brand.pairing == PairingMethod.PRE_SHARED_KEY && credentials.get(device.ip) == null) {
            throw TvError.MissingKey()
        }

        session.end()
        val controller = controllerFactory.create(device)
        try {
            controller.connect()
        } catch (e: Throwable) {
            // Throwable incluye la cancelación: pase lo que pase, cerramos y relanzamos el mismo error.
            controller.disconnect()
            throw e
        }
        session.start(controller)
        lastDevice.save(device)
    }
}
