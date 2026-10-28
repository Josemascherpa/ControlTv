package ar.com.josemascherpa.controltv.data.tv.samsung

import android.util.Base64
import ar.com.josemascherpa.controltv.data.network.call
import ar.com.josemascherpa.controltv.data.network.openWebSocket
import ar.com.josemascherpa.controltv.data.network.sendOrThrow
import ar.com.josemascherpa.controltv.domain.controller.HardwareAddress
import ar.com.josemascherpa.controltv.domain.controller.PointerInput
import ar.com.josemascherpa.controltv.domain.controller.PowerOn
import ar.com.josemascherpa.controltv.domain.controller.TextInput
import ar.com.josemascherpa.controltv.domain.controller.TvController
import ar.com.josemascherpa.controltv.domain.controller.WebBrowser
import ar.com.josemascherpa.controltv.domain.controller.scrollWithArrows
import ar.com.josemascherpa.controltv.domain.error.TvError
import ar.com.josemascherpa.controltv.domain.model.RemoteKey
import ar.com.josemascherpa.controltv.domain.model.ScrollDirection
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import ar.com.josemascherpa.controltv.domain.repository.CredentialStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import org.json.JSONException
import org.json.JSONObject

/**
 * Samsung (Tizen, modelos 2016+): se abre un WebSocket a
 * wss://IP:8002/api/v2/channels/samsung.remote.control y se mandan mensajes JSON.
 * La primera vez la TV muestra "¿Permitir?" y, al aceptar, devuelve un token que guardamos.
 *
 * Además, http://IP:8001/api/v2/ contesta sin emparejar con datos de la TV: su MAC ("wifiMac")
 * y, en los modelos nuevos, si está en espera ("PowerState").
 */
class SamsungController(
    override val device: TvDevice,
    private val credentials: CredentialStore,
    private val client: OkHttpClient,
    private val http: OkHttpClient,
) : TvController, TextInput, WebBrowser, PointerInput, HardwareAddress, PowerOn {

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

    // Samsung tiene un "mouse" propio (TypeOfRemote = ProcessMouseDevice) que usa posiciones.
    override suspend fun movePointer(dx: Int, dy: Int) = sendMouse("Move", dx, dy)

    override suspend fun click() = sendMouse("LeftClick", 0, 0)

    private fun sendMouse(cmd: String, x: Int, y: Int) {
        val position = JSONObject()
            .put("x", x)
            .put("y", y)
            .put("Time", System.currentTimeMillis().toString())
        val params = JSONObject()
            .put("Cmd", cmd)
            .put("Position", position)
            .put("TypeOfRemote", "ProcessMouseDevice")
        socket.sendOrThrow(JSONObject().put("method", "ms.remote.control").put("params", params).toString())
    }

    private fun sendRemote(cmd: String, data: String, type: String) {
        val params = JSONObject()
            .put("Cmd", cmd)
            .put("DataOfCmd", data)
            .put("Option", "false")
            .put("TypeOfRemote", type)
        socket.sendOrThrow(JSONObject().put("method", "ms.remote.control").put("params", params).toString())
    }

    override suspend fun macAddresses(): List<String> =
        listOfNotNull(deviceInfo().optString("wifiMac").takeIf { it.isNotEmpty() })

    // Algunas Samsung en espera siguen atendiendo la red: ahí el botón de encendido las prende.
    override suspend fun turnOn() {
        if (deviceInfo().optString("PowerState") == "standby") sendKey(RemoteKey.POWER)
    }

    private suspend fun deviceInfo(): JSONObject {
        val response = http.call(Request.Builder().url("http://${device.ip}:$INFO_PORT/api/v2/").build())
        return try {
            JSONObject(response.body).getJSONObject("device")
        } catch (e: JSONException) {
            throw TvError.DeviceError(detail = "datos de la TV inválidos")
        }
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
        const val INFO_PORT = 8001
    }
}
