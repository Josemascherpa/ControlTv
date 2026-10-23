package com.example.controltv.ui.messages

import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.PairingMethod
import com.example.controltv.domain.model.TvFeature

/**
 * Los avisos que la app le puede mostrar a la persona. El ViewModel elige cuál;
 * el texto se arma acá, en un solo lugar, en español simple y diciendo qué hacer.
 */
sealed interface UserMessage {
    data object NoTvsFound : UserMessage

    data class NoTvAtIp(val ip: String) : UserMessage

    /** Un error de la TV. [pairing] permite dar la ayuda justa para esa marca. */
    data class Failure(val error: TvError, val pairing: PairingMethod?) : UserMessage
}

fun UserMessage.text(): String = when (this) {
    UserMessage.NoTvsFound ->
        "No se encontró ninguna TV. Fijate que esté prendida y en el mismo WiFi que el teléfono. " +
            "Si es Google TV o Fire TV, tocá «¿Tenés Google TV o Fire TV y no aparece?»."
    is UserMessage.NoTvAtIp ->
        "No se encontró ninguna TV en $ip. Revisá el número en la TV, en Ajustes > Red > Estado de la red."
    is UserMessage.Failure -> error.text(pairing)
}

// Como TvError es "sealed", si mañana se agrega un error nuevo este "when" no compila hasta
// que le escribamos su mensaje. Así ningún error queda sin explicar.
private fun TvError.text(pairing: PairingMethod?): String = when (this) {
    is TvError.Unreachable ->
        "No se pudo conectar con la TV. Fijate que esté prendida y en el mismo WiFi que el teléfono."
    is TvError.ConnectionLost, is TvError.NotConnected ->
        "Se cortó la conexión con la TV. Tocá \"Cambiar TV\" y volvé a conectarla."
    is TvError.NoResponse ->
        "La TV no contestó. Probá de nuevo en unos segundos."
    is TvError.PairingTimeout -> when (pairing) {
        PairingMethod.ALLOW_DEBUGGING -> "La TV no respondió. Cuando aparezca \"¿Permitir depuración?\", elegí Permitir."
        else -> "La TV no respondió. Cuando aparezca el mensaje en la TV, aceptalo con el control."
    }
    is TvError.PairingCodeRequired ->
        "Escribí el código que aparece en la pantalla de la TV."
    is TvError.WrongPairingCode ->
        "El código no coincide. Fijate bien en la TV y escribilo de nuevo."
    is TvError.PairingRejected -> when (pairing) {
        PairingMethod.ALLOW_DEBUGGING ->
            "No se pudo conectar. En la TV activá la depuración en Ajustes > Sistema > Opciones de " +
                "desarrollador y aceptá el mensaje que aparece."
        PairingMethod.CODE_ON_TV -> "La TV no aceptó el código. Volvé a conectar: va a aparecer un código nuevo."
        else -> "La TV rechazó la conexión. Volvé a intentar y elegí \"Permitir\" en la TV."
    }
    is TvError.RemoteControlDisabled ->
        "La TV no permite usar el teléfono como control. En la TV activá Ajustes > Sistema > " +
            "Configuración avanzada > Control por apps móviles."
    is TvError.MissingKey ->
        "Falta la clave de la TV."
    is TvError.WrongKey ->
        "La clave no es correcta. Revisala en la TV, en Ajustes > Red > Control IP, y volvé a conectar."
    is TvError.NoBrowser ->
        "Esta TV no tiene navegador de internet. Instalá uno gratis desde Google Play en la TV " +
            "(por ejemplo \"TV Bro\") y probá de nuevo."
    is TvError.KeyNotSupported ->
        "Esta TV no tiene ese botón."
    is TvError.FeatureNotSupported -> when (feature) {
        TvFeature.TEXT -> "Esta TV no permite escribir desde el teléfono."
        TvFeature.BROWSER -> "Esta TV no puede abrir páginas de internet."
        TvFeature.CONTENT_SEARCH -> "Esta TV no permite buscar desde el teléfono."
    }
    is TvError.DeviceError ->
        "La TV no pudo hacer lo que le pediste. Probá de nuevo."
}

/** Lo que se muestra mientras conecta: qué tiene que hacer la persona en la TV. */
fun PairingMethod.connectingHint(): String = when (this) {
    PairingMethod.ACCEPT_ON_TV -> "Si en la TV aparece un mensaje pidiendo permiso, aceptalo con el control."
    PairingMethod.CODE_ON_TV -> "Si es la primera vez, en la TV va a aparecer un código para escribir en el teléfono."
    PairingMethod.ALLOW_DEBUGGING -> "En la TV aceptá \"¿Permitir depuración?\". Si no aparece, activá la " +
        "depuración en Ajustes > Sistema > Opciones de desarrollador."
    PairingMethod.ENABLE_MOBILE_CONTROL -> "Si no conecta, en la TV activá Ajustes > Sistema > " +
        "Configuración avanzada > Control por apps móviles."
    PairingMethod.PRE_SHARED_KEY -> "Conectando con la clave que escribiste…"
}
