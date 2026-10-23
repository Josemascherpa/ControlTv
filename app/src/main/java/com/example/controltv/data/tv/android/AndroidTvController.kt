package com.example.controltv.data.tv.android

import com.example.controltv.data.network.ioCall
import com.example.controltv.domain.controller.TextInput
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.WebBrowser
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

/**
 * Google TV / Android TV / Fire TV por ADB (Android Debug Bridge) en el puerto 5555.
 * Es lo mismo que usa Android Studio para instalar apps, pero por WiFi.
 *
 * ADB nos deja ejecutar comandos de consola en la TV, por ejemplo:
 *   input keyevent KEYCODE_HOME   → aprieta "Inicio"
 *   input text hola               → escribe "hola"
 *   am start -d https://...       → abre un link
 *
 * Requisito: en la TV activar Opciones de desarrollador > Depuración por USB/red.
 * La primera vez la TV pregunta "¿Permitir depuración?" y hay que aceptar.
 */
class AndroidTvController(
    override val device: TvDevice,
    private val keyDir: File,
) : TvController, TextInput, WebBrowser {

    private var dadb: Dadb? = null

    // dadb es "bloqueante" (frena el hilo mientras espera la red); ioCall lo corre en Dispatchers.IO.
    override suspend fun connect() = ioCall(onError = { TvError.PairingRejected(it) }) {
        val connection = Dadb.create(device.ip, ADB_PORT, loadOrCreateKeyPair())
        try {
            // Un comando inofensivo para forzar la conexión y el cartel de autorización.
            connection.shell("echo ok")
        } catch (e: Exception) {
            connection.close()
            throw e
        }
        dadb = connection
    }

    // ADB necesita un par de claves RSA para identificarnos. Las creamos una vez y las guardamos,
    // así la TV nos recuerda y no vuelve a preguntar.
    private fun loadOrCreateKeyPair(): AdbKeyPair {
        val privateKey = File(keyDir, "adbkey")
        val publicKey = File(keyDir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) AdbKeyPair.generate(privateKey, publicKey)
        return AdbKeyPair.read(privateKey, publicKey)
    }

    /** Ejecuta un comando en la TV y devuelve todo lo que imprimió. */
    private suspend fun shell(command: String): String {
        val connection = dadb ?: throw TvError.NotConnected()
        return ioCall { connection.shell(command).allOutput }
    }

    override suspend fun sendKey(key: RemoteKey) {
        shell("input keyevent ${keyCode(key)}")
    }

    // "input text" no acepta espacios: hay que mandarlos como %s.
    // Ojo: tampoco soporta acentos ni ñ (limitación de Android).
    override suspend fun sendText(text: String) {
        shell("input text ${quote(text.replace(" ", "%s"))}")
    }

    override suspend fun sendEnter() {
        shell("input keyevent KEYCODE_ENTER")
    }

    override suspend fun openUrl(url: String) {
        val output = shell("am start -a android.intent.action.VIEW -d ${quote(url)}")
        // Muchas Google TV no traen navegador: Android contesta "unable to resolve Intent".
        if ("Error" in output) throw TvError.NoBrowser()
    }

    // Avanzar/retroceder página es más cómodo que ir flecha por flecha.
    override suspend fun scroll(direction: ScrollDirection) {
        val key = when (direction) {
            ScrollDirection.UP -> "KEYCODE_PAGE_UP"
            ScrollDirection.DOWN -> "KEYCODE_PAGE_DOWN"
        }
        shell("input keyevent $key")
    }

    // Envuelve en comillas simples para que la consola no interprete caracteres como & o ?.
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun keyCode(key: RemoteKey): String = when (key) {
        RemoteKey.POWER -> "KEYCODE_POWER"
        RemoteKey.HOME -> "KEYCODE_HOME"
        RemoteKey.BACK -> "KEYCODE_BACK"
        RemoteKey.MENU -> "KEYCODE_MENU"
        RemoteKey.UP -> "KEYCODE_DPAD_UP"
        RemoteKey.DOWN -> "KEYCODE_DPAD_DOWN"
        RemoteKey.LEFT -> "KEYCODE_DPAD_LEFT"
        RemoteKey.RIGHT -> "KEYCODE_DPAD_RIGHT"
        RemoteKey.OK -> "KEYCODE_DPAD_CENTER"
        RemoteKey.VOLUME_UP -> "KEYCODE_VOLUME_UP"
        RemoteKey.VOLUME_DOWN -> "KEYCODE_VOLUME_DOWN"
        RemoteKey.MUTE -> "KEYCODE_VOLUME_MUTE"
        RemoteKey.CHANNEL_UP -> "KEYCODE_CHANNEL_UP"
        RemoteKey.CHANNEL_DOWN -> "KEYCODE_CHANNEL_DOWN"
        RemoteKey.PLAY_PAUSE -> "KEYCODE_MEDIA_PLAY_PAUSE"
    }

    override fun disconnect() {
        // Cerrar puede fallar si la conexión ya estaba caída; en ese caso no hay nada que hacer.
        try {
            dadb?.close()
        } catch (e: Exception) {
            // ignorado a propósito
        }
        dadb = null
    }

    companion object {
        const val ADB_PORT = 5555
    }
}
