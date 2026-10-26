package com.example.controltv.domain.controller

import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.model.TvFeature

// Una "interface" define QUÉ se puede hacer, sin decir CÓMO. Cada marca la implementa a su manera
// y el resto de la app no necesita saber de qué marca es la TV.
//
// En vez de una interfaz gigante, hay una básica (la tienen todas las TVs) y otras chicas para
// las funciones extra. Cada TV implementa sólo las que puede cumplir (la "I" de SOLID).
//
// "suspend" marca funciones que pueden tardar (red) sin bloquear la pantalla.
// Sólo se pueden llamar desde una corrutina (lo vemos en los ViewModels).
// Todas lanzan [TvError] si algo sale mal, nunca otro tipo de excepción.

/** Lo que tienen todas las TVs: conectarse y apretar botones. */
interface TvController {
    val device: TvDevice

    /** Conecta y empareja con la TV. */
    suspend fun connect()

    suspend fun sendKey(key: RemoteKey)

    fun disconnect()
}

/** TVs a las que se les puede escribir texto (en el buscador, navegador, etc). */
interface TextInput {
    suspend fun sendText(text: String)

    suspend fun sendEnter()
}

/** TVs que tienen navegador de internet. */
interface WebBrowser {
    suspend fun openUrl(url: String)

    /** Sube o baja la página que está abierta. */
    suspend fun scroll(direction: ScrollDirection)
}

/** TVs sin navegador que igual pueden buscar contenido en sus aplicaciones (ej. Roku). */
interface ContentSearch {
    suspend fun search(query: String)
}

/** TVs que tienen un cursor en pantalla que se puede mover y tocar, como un mouse (ej. LG y Samsung). */
interface PointerInput {
    /** Mueve el cursor. [dx] y [dy] son cuánto se corrió el dedo (positivo = derecha / abajo). */
    suspend fun movePointer(dx: Int, dy: Int)

    /** Hace click donde está el cursor. */
    suspend fun click()
}

/**
 * TVs que se emparejan con un código que aparece en la pantalla (Google TV).
 * Cuando hace falta, [TvController.connect] le pide el código a la TV y lanza
 * [TvError.PairingCodeRequired]; después se llama a [finishPairing] con lo que escribió la persona.
 */
interface CodePairing {
    /** @throws TvError.WrongPairingCode si el código no coincide. */
    suspend fun finishPairing(code: String)
}

/**
 * TVs que nos pueden decir su "dirección física" (MAC): un número fijo de su placa de red.
 * Lo guardamos mientras está prendida porque, apagada, la única forma de despertarla es mandarle
 * por WiFi un aviso especial ("Wake-on-LAN") dirigido a ese número.
 */
interface HardwareAddress {
    /** Las MAC de la TV (WiFi y cable, si tiene las dos), ej. "A4:30:7A:12:34:56". Vacía si no las sabe. */
    suspend fun macAddresses(): List<String>
}

/**
 * TVs que en modo de espera siguen escuchando por la red y se pueden prender con su propio
 * protocolo (ej. Roku con "Inicio rápido", Google TV, Sony con "Encendido remoto").
 */
interface PowerOn {
    /** Prende la pantalla. Si ya estaba prendida, no hace nada (no la apaga). */
    suspend fun turnOn()
}

/** Qué funciones extra tiene esta TV, según las interfaces que implementa. */
val TvController.features: Set<TvFeature>
    get() = buildSet {
        if (this@features is TextInput) add(TvFeature.TEXT)
        if (this@features is WebBrowser) add(TvFeature.BROWSER)
        if (this@features is ContentSearch) add(TvFeature.CONTENT_SEARCH)
        if (this@features is PointerInput) add(TvFeature.POINTER)
    }

// "as?" intenta convertir al tipo pedido y da null si no se puede. Con "?:" lanzamos
// el error declarado para ese caso, en vez de fallar con un error genérico.
fun TvController.asTextInput(): TextInput =
    this as? TextInput ?: throw TvError.FeatureNotSupported(TvFeature.TEXT)

fun TvController.asWebBrowser(): WebBrowser =
    this as? WebBrowser ?: throw TvError.FeatureNotSupported(TvFeature.BROWSER)

fun TvController.asPointerInput(): PointerInput =
    this as? PointerInput ?: throw TvError.FeatureNotSupported(TvFeature.POINTER)

/**
 * Forma "por defecto" de mover una página: con las flechas, que en casi todos los navegadores
 * de TV mueven la página. Las marcas que tienen algo mejor (ej. la ruedita de LG) no la usan.
 */
suspend fun TvController.scrollWithArrows(direction: ScrollDirection) = repeat(3) {
    sendKey(direction.arrow)
}
