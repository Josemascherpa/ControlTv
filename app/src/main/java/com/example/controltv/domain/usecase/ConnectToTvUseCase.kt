package com.example.controltv.domain.usecase

import com.example.controltv.domain.controller.CodePairing
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.TvControllerFactory
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.PairingMethod
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import com.example.controltv.domain.repository.LastDeviceStore
import com.example.controltv.domain.repository.PairedDevices
import com.example.controltv.domain.session.TvSession

/**
 * Un "caso de uso" es una acción concreta de la app, con su lógica en un solo lugar.
 * Este conecta una TV: guarda la clave si la pasaron, crea el controlador, empareja
 * y la deja como TV actual.
 *
 * Con las TVs que piden un código (Google TV) la conexión queda "en pausa" mientras la persona
 * lo escribe: [invoke] lanza [TvError.PairingCodeRequired] y después se sigue con [submitPairingCode].
 *
 * Las dependencias llegan por el constructor (no las crea él), así en un test se le pueden
 * pasar versiones de prueba.
 */
class ConnectToTvUseCase(
    private val controllerFactory: TvControllerFactory,
    private val credentials: CredentialStore,
    private val lastDevice: LastDeviceStore,
    private val pairedDevices: PairedDevices,
    private val session: TvSession,
) {
    // La TV que está esperando el código de emparejamiento, si hay una.
    private var waitingForCode: TvController? = null

    /**
     * true si antes de conectar hay que explicarle a la persona cómo preparar la TV:
     * las que necesitan la depuración activada y nunca se conectaron desde este teléfono.
     */
    fun needsSetup(device: TvDevice): Boolean =
        device.brand.pairing == PairingMethod.ALLOW_DEBUGGING && !pairedDevices.isPaired(device.ip)

    /**
     * "operator fun invoke" permite llamarlo como si fuera una función: connectToTv(device).
     * @param key la clave que escribió la persona, para las TVs que la piden (Sony).
     * @throws TvError.MissingKey si la TV necesita clave y todavía no la tenemos.
     * @throws TvError.PairingCodeRequired si la TV muestra un código que hay que escribir.
     */
    suspend operator fun invoke(device: TvDevice, key: String? = null) {
        key?.trim()?.takeIf { it.isNotEmpty() }?.let { credentials.save(device.ip, it) }
        if (device.brand.pairing == PairingMethod.PRE_SHARED_KEY && credentials.get(device.ip) == null) {
            throw TvError.MissingKey()
        }

        cancelPairing()
        session.end()
        val controller = controllerFactory.create(device)
        connectOrClose(controller)
    }

    /**
     * Sigue la conexión que quedó esperando el código.
     * @throws TvError.WrongPairingCode si el código no coincide (se puede volver a escribir).
     */
    suspend fun submitPairingCode(code: String) {
        val controller = waitingForCode ?: throw TvError.NotConnected()
        val pairing = controller as? CodePairing ?: throw TvError.NotConnected()
        try {
            pairing.finishPairing(code)
        } catch (e: TvError.WrongPairingCode) {
            throw e // seguimos esperando: la persona lo puede volver a escribir
        } catch (e: Throwable) {
            waitingForCode = null
            controller.disconnect()
            throw e
        }
        waitingForCode = null
        connectOrClose(controller)
    }

    /** La persona cerró el cartel del código: soltamos la TV. */
    fun cancelPairing() {
        waitingForCode?.disconnect()
        waitingForCode = null
    }

    private suspend fun connectOrClose(controller: TvController) {
        try {
            controller.connect()
        } catch (e: TvError.PairingCodeRequired) {
            // No cerramos: la TV está mostrando el código y esperamos a que la persona lo escriba.
            waitingForCode = controller
            throw e
        } catch (e: Throwable) {
            // Throwable incluye la cancelación: pase lo que pase, cerramos y relanzamos el mismo error.
            controller.disconnect()
            throw e
        }
        session.start(controller)
        lastDevice.save(controller.device)
        pairedDevices.markPaired(controller.device.ip)
    }
}
