package com.example.controltv.domain.error

import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.TvFeature

/**
 * Todos los errores que puede dar la app al hablar con una TV.
 *
 * "sealed" = sólo existen estas variantes. Así, cuando la UI arma el mensaje con un "when",
 * el compilador la obliga a contemplar todos los casos (si mañana agregamos uno, avisa).
 *
 * Fijate que no tienen texto para la persona: eso lo decide la UI. Acá sólo decimos QUÉ pasó.
 * "cause" guarda el error técnico original (útil para depurar, nunca se le muestra a nadie).
 */
sealed class TvError(cause: Throwable? = null) : Exception(cause) {

    /** No se pudo llegar a la TV: está apagada, en otra red o la IP es incorrecta. */
    class Unreachable(cause: Throwable? = null) : TvError(cause)

    /** Estábamos conectados y se cortó. */
    class ConnectionLost(cause: Throwable? = null) : TvError(cause)

    /** Se intentó mandar algo sin tener una TV conectada. */
    class NotConnected : TvError()

    /** La TV recibió el pedido pero no contestó a tiempo. */
    class NoResponse : TvError()

    /** La persona no aceptó el mensaje de permiso en la TV a tiempo. */
    class PairingTimeout : TvError()

    /** La TV rechazó la conexión (o falta activar la depuración, en Android TV). */
    class PairingRejected(cause: Throwable? = null) : TvError(cause)

    /** La TV tiene desactivado el control desde el teléfono (Roku: "Control por apps móviles"). */
    class RemoteControlDisabled : TvError()

    /** La TV necesita una clave y todavía no la tenemos (Sony). */
    class MissingKey : TvError()

    /** La clave guardada no es la que está configurada en la TV. */
    class WrongKey : TvError()

    /** La TV podría abrir páginas pero no tiene ningún navegador instalado. */
    class NoBrowser : TvError()

    /** Esta TV no tiene ese botón. */
    class KeyNotSupported(val key: RemoteKey) : TvError()

    /** Esta TV no tiene esa función (ej. Roku no puede abrir páginas web). */
    class FeatureNotSupported(val feature: TvFeature) : TvError()

    /** La TV contestó con un error propio. [code] y [detail] son para depurar. */
    class DeviceError(val code: Int? = null, val detail: String? = null) : TvError()
}
