package com.example.controltv.domain.usecase

import com.example.controltv.domain.controller.PowerOn
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.KnownTvs
import com.example.controltv.domain.repository.TvWaker
import com.example.controltv.domain.session.TvSession
import kotlinx.coroutines.delay

/**
 * Prende una TV apagada.
 *
 * El control físico usa infrarrojo, que funciona siempre. El teléfono, en cambio, habla por WiFi,
 * y muchas TVs apagadas no atienden la red. Por eso hay dos caminos, y probamos los dos:
 *   1. "Wake-on-LAN": un aviso especial por WiFi dirigido a la MAC de la TV, que la placa de red
 *      escucha aunque la TV esté apagada (si la opción está activada en la TV).
 *   2. Si la TV quedó en modo de espera atendiendo la red, nos conectamos y le pedimos que se
 *      prenda con su propio protocolo ([PowerOn]).
 *
 * @param pause cuánto esperar entre intentos. Se recibe por el constructor para que los tests no esperen de verdad.
 */
class TurnOnTvUseCase(
    private val waker: TvWaker,
    private val knownTvs: KnownTvs,
    private val connectToTv: ConnectToTvUseCase,
    private val session: TvSession,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {

    /**
     * Prende la TV y la deja conectada.
     * @throws TvError.CantTurnOn si después de varios intentos la TV no aparece.
     */
    suspend operator fun invoke(device: TvDevice) {
        val macs = knownTvs.find(device.ip)?.macAddresses.orEmpty()
        // Sin MAC sólo nos queda el camino 2, que si no contesta a la primera no va a contestar después.
        val attempts = if (macs.isEmpty()) 1 else ATTEMPTS
        repeat(attempts) { attempt ->
            if (macs.isNotEmpty()) waker.wake(device.ip, macs)
            if (attempt > 0 || macs.isNotEmpty()) pause(WAIT_MS)
            try {
                connectToTv(device)
                (session.current() as? PowerOn)?.turnOn()
                return
            } catch (e: TvError) {
                // Todavía no prendió (o no atiende la red): volvemos a mandar el aviso y esperamos.
                // Los demás errores (ej. falta el código de Google TV) no se arreglan esperando.
                if (!e.meansTvIsOff()) throw e
            }
        }
        throw TvError.CantTurnOn()
    }

    /**
     * El botón de encendido del control. Si la TV contesta, lo aprieta (prende o apaga, como el
     * control físico). Si no contesta es porque está apagada: entonces la prende.
     * @param onTurningOn avisa que empezó a prenderla (tarda unos segundos), para mostrarlo en pantalla.
     */
    suspend fun pressPowerButton(onTurningOn: () -> Unit = {}) {
        val controller = session.current()
        try {
            controller.sendKey(RemoteKey.POWER)
        } catch (e: TvError) {
            if (!e.meansTvIsOff()) throw e
            onTurningOn()
            invoke(controller.device)
        }
    }

    // Los errores que da una TV apagada: no se la encuentra o se cortó la conexión.
    private fun TvError.meansTvIsOff(): Boolean =
        this is TvError.Unreachable || this is TvError.ConnectionLost ||
            this is TvError.NotConnected || this is TvError.NoResponse

    private companion object {
        const val ATTEMPTS = 8
        // Una TV tarda unos segundos en prender la placa de red y abrir sus servicios.
        const val WAIT_MS = 3_000L
    }
}
