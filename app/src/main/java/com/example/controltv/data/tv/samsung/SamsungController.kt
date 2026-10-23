package com.example.controltv.data.tv.samsung

import android.util.Base64
import com.example.controltv.data.network.openWebSocket
import com.example.controltv.data.network.sendOrThrow
import com.example.controltv.domain.controller.TextInput
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.WebBrowser
import com.example.controltv.domain.controller.scrollWithArrows
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.json.JSONException
import org.json.JSONObject

/**
 * Samsung (Tizen, modelos 2016+): se abre un WebSocket a
 * wss://IP:8002/api/v2/channels/samsung.remote.control y se mandan mensajes JSON.
 * La primera vez la TV muestra "¿Permitir?" y, al aceptar, devuelve un token que guardamos.
 */
class SamsungController(
    override val device: TvDevice,
    private val credentials: CredentialStore,
    private val client: OkHttpClient,
) : TvController, TextInput, WebBrowser {

    private var socket: WebSocket? = null

    override suspend fun connect() {
        val ready = CompletableDeferred<Unit>()
        val onText: (String) -> Unit = { text -> handleMessage(text, ready) }

        // Los modelos nuevos exigen la conexión segura (8002); los viejos sólo tienen la 8001.
        socket = try {
            client.openWebSocket(socketUrl(secure = true), onText)
        } catch (e: TvError.Unreachable) {
            client.openWebSocket(socketUrl(secure = false), onText)
        }

        // Le damos tiempo a la persona para aceptar el cartel en la TV.
        // Si la TV rechaza, ready.await() lanza el TvError con el que se completó.
        withTimeoutOrNull(PAIRING_TIMEOUT_MS) { ready.await() } ?: throw TvError.PairingTimeout()
    }

    private fun socketUrl(secure: Boolean): String {
        val appName = Base64.encodeToString(APP_NAME.toByteArray(), Base64.NO_WRAP)
        val base = if (secure) "wss://${device.ip}:8002" else "ws://${device.ip}:8001"
        val token = credentials.get(device.ip)?.let { "&token=$it" }.orEmpty()
        return "$base/api/v2/channels/samsung.remote.control?name=$appName$token"
    }

    // Se llama desde el hilo de OkHttp. Un mensaje que no es JSON se ignora.
    private fun handleMessage(text: String, ready: CompletableDeferred<Unit>) {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return
        }
        when (json.optString("event")) {
            "ms.channel.connect" -> {
                json.optJSONObject("data")?.optString("token")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { credentials.save(device.ip, it) }
                ready.complete(Unit)
            }
            "ms.channel.unauthorized" -> ready.completeExceptionally(TvError.PairingRejected())
        }
    }

    override suspend fun sendKey(key: RemoteKey) = sendRemote(
        cmd = "Click",
        data = keyCode(key),
        type = "SendRemoteKey",
    )

    // Samsung recibe el texto en Base64. Sólo funciona si en la TV hay un teclado abierto.
    override suspend fun sendText(text: String) = sendRemote(
        cmd = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP),
        data = "base64",
        type = "SendInputString",
    )

    override suspend fun sendEnter() = sendKey(RemoteKey.OK)

    override suspend fun openUrl(url: String) {
        val data = JSONObject()
            .put("appId", "org.tizen.browser")
            .put("action_type", "NATIVE_LAUNCH")
            .put("metaTag", url)
        val params = JSONObject().put("event", "ed.apps.launch").put("to", "host").put("data", data)
        socket.sendOrThrow(JSONObject().put("method", "ms.channel.emit").put("params", params).toString())
    }

    override suspend fun scroll(direction: ScrollDirection) = scrollWithArrows(direction)

    private fun sendRemote(cmd: String, data: String, type: String) {
        val params = JSONObject()
            .put("Cmd", cmd)
            .put("DataOfCmd", data)
            .put("Option", "false")
            .put("TypeOfRemote", type)
        socket.sendOrThrow(JSONObject().put("method", "ms.remote.control").put("params", params).toString())
    }

    // "when" es como un switch: traduce nuestro botón al código de Samsung.
    private fun keyCode(key: RemoteKey): String = when (key) {
        RemoteKey.POWER -> "KEY_POWER"
        RemoteKey.HOME -> "KEY_HOME"
        RemoteKey.BACK -> "KEY_RETURN"
        RemoteKey.MENU -> "KEY_MENU"
        RemoteKey.UP -> "KEY_UP"
        RemoteKey.DOWN -> "KEY_DOWN"
        RemoteKey.LEFT -> "KEY_LEFT"
        RemoteKey.RIGHT -> "KEY_RIGHT"
        RemoteKey.OK -> "KEY_ENTER"
        RemoteKey.VOLUME_UP -> "KEY_VOLUP"
        RemoteKey.VOLUME_DOWN -> "KEY_VOLDOWN"
        RemoteKey.MUTE -> "KEY_MUTE"
        RemoteKey.CHANNEL_UP -> "KEY_CHUP"
        RemoteKey.CHANNEL_DOWN -> "KEY_CHDOWN"
        RemoteKey.PLAY_PAUSE -> "KEY_PLAY"
    }

    override fun disconnect() {
        socket?.close(1000, null)
        socket = null
    }

    private companion object {
        const val APP_NAME = "ControlTv"
        const val PAIRING_TIMEOUT_MS = 60_000L
    }
}
