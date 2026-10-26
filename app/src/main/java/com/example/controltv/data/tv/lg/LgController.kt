package com.example.controltv.data.tv.lg

import com.example.controltv.data.network.openWebSocket
import com.example.controltv.data.network.sendOrThrow
import com.example.controltv.domain.controller.HardwareAddress
import com.example.controltv.domain.controller.PointerInput
import com.example.controltv.domain.controller.TextInput
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.WebBrowser
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LG (webOS): protocolo "SSAP" sobre WebSocket en el puerto 3001 (seguro) o 3000.
 *
 * 1. Nos "registramos": la TV muestra un cartel y, al aceptar, nos da una client-key.
 * 2. Cada acción es un pedido con una URI tipo "ssap://audio/volumeUp".
 * 3. Las flechas/OK/Atrás van por un segundo socket, el "pointer socket" (el del Magic Remote).
 */
class LgController(
    override val device: TvDevice,
    private val credentials: CredentialStore,
    private val client: OkHttpClient,
) : TvController, TextInput, WebBrowser, PointerInput, HardwareAddress {

    private var socket: WebSocket? = null
    private var pointer: WebSocket? = null
    private var registered = CompletableDeferred<Unit>()

    // Cada pedido lleva un id; cuando llega la respuesta con ese id, completamos su "deferred".
    // ConcurrentHashMap porque las respuestas llegan desde otro hilo (el de OkHttp).
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val nextId = AtomicInteger()

    override suspend fun connect() {
        registered = CompletableDeferred()
        socket = try {
            client.openWebSocket("wss://${device.ip}:3001", ::handleMessage)
        } catch (e: TvError.Unreachable) {
            client.openWebSocket("ws://${device.ip}:3000", ::handleMessage)
        }

        socket.sendOrThrow(registerMessage().toString())
        withTimeoutOrNull(PAIRING_TIMEOUT_MS) { registered.await() } ?: throw TvError.PairingTimeout()

        val socketPath = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
            .optString("socketPath")
            .takeIf { it.isNotEmpty() } ?: throw TvError.DeviceError(detail = "sin pointer socket")
        pointer = client.openWebSocket(socketPath, onText = {})
    }

    private fun registerMessage(): JSONObject {
        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "1.0")
            .put("permissions", JSONArray(PERMISSIONS))
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", manifest)
        credentials.get(device.ip)?.let { payload.put("client-key", it) }
        return JSONObject().put("type", "register").put("id", REGISTER_ID).put("payload", payload)
    }

    // Se llama desde el hilo de OkHttp. Un mensaje que no es JSON se ignora.
    private fun handleMessage(text: String) {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return
        }
        val id = json.optString("id")
        val type = json.optString("type")

        if (id == REGISTER_ID) {
            when (type) {
                "registered" -> {
                    json.optJSONObject("payload")?.optString("client-key")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { credentials.save(device.ip, it) }
                    registered.complete(Unit)
                }
                "error" -> registered.completeExceptionally(TvError.PairingRejected())
                // "response" con pairingType PROMPT = la TV está mostrando el cartel; seguimos esperando.
            }
            return
        }

        val deferred = pending.remove(id) ?: return
        if (type == "error") {
            deferred.completeExceptionally(TvError.DeviceError(detail = json.optString("error")))
        } else {
            deferred.complete(json.optJSONObject("payload") ?: JSONObject())
        }
    }

    private suspend fun request(uri: String, payload: JSONObject? = null): JSONObject {
        val id = "req_${nextId.incrementAndGet()}"
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val message = JSONObject().put("type", "request").put("id", id).put("uri", uri)
        if (payload != null) message.put("payload", payload)
        return try {
            socket.sendOrThrow(message.toString())
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() } ?: throw TvError.NoResponse()
        } finally {
            pending.remove(id)
        }
    }

    override suspend fun sendKey(key: RemoteKey) {
        if (key == RemoteKey.POWER) {
            request("ssap://system/turnOff")
        } else {
            // El pointer socket usa un formato de texto simple, no JSON.
            pointer.sendOrThrow("type:button\nname:${buttonName(key)}\n\n")
        }
    }

    override suspend fun sendText(text: String) {
        request("ssap://com.webos.service.ime/insertText", JSONObject().put("text", text).put("replace", 0))
    }

    override suspend fun sendEnter() {
        request("ssap://com.webos.service.ime/sendEnterKey")
    }

    override suspend fun openUrl(url: String) {
        request("ssap://system.launcher/open", JSONObject().put("target", url))
    }

    // El pointer socket también entiende la "ruedita" del Magic Remote.
    override suspend fun scroll(direction: ScrollDirection) {
        val (dx, dy) = when (direction) {
            ScrollDirection.UP -> 0 to -SCROLL_STEP
            ScrollDirection.DOWN -> 0 to SCROLL_STEP
            ScrollDirection.LEFT -> -SCROLL_STEP to 0
            ScrollDirection.RIGHT -> SCROLL_STEP to 0
        }
        pointer.sendOrThrow("type:scroll\ndx:$dx\ndy:$dy\n\n")
    }

    // El pointer socket es el del Magic Remote: "move" mueve el cursor y "click" hace click.
    override suspend fun movePointer(dx: Int, dy: Int) {
        pointer.sendOrThrow("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")
    }

    override suspend fun click() {
        pointer.sendOrThrow("type:click\n\n")
    }

    // La TV informa sus dos placas de red (WiFi y cable), cada una con su MAC.
    override suspend fun macAddresses(): List<String> {
        val info = request("ssap://com.webos.service.connectionmanager/getinfo")
        return listOf("wifiInfo", "wiredInfo").mapNotNull { network ->
            info.optJSONObject(network)?.optString("macAddress")?.takeIf { it.isNotEmpty() }
        }
    }

    private fun buttonName(key: RemoteKey): String = when (key) {
        RemoteKey.POWER -> "POWER"
        RemoteKey.HOME -> "HOME"
        RemoteKey.BACK -> "BACK"
        RemoteKey.MENU -> "MENU"
        RemoteKey.UP -> "UP"
        RemoteKey.DOWN -> "DOWN"
        RemoteKey.LEFT -> "LEFT"
        RemoteKey.RIGHT -> "RIGHT"
        RemoteKey.OK -> "ENTER"
        RemoteKey.VOLUME_UP -> "VOLUMEUP"
        RemoteKey.VOLUME_DOWN -> "VOLUMEDOWN"
        RemoteKey.MUTE -> "MUTE"
        RemoteKey.CHANNEL_UP -> "CHANNELUP"
        RemoteKey.CHANNEL_DOWN -> "CHANNELDOWN"
        RemoteKey.PLAY_PAUSE -> "PLAY"
    }

    override fun disconnect() {
        pointer?.close(1000, null)
        socket?.close(1000, null)
        pointer = null
        socket = null
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    // "companion object" = cosas que pertenecen a la clase y no a cada instancia (como "static" en Java).
    private companion object {
        const val REGISTER_ID = "register_0"
        const val PAIRING_TIMEOUT_MS = 60_000L
        const val REQUEST_TIMEOUT_MS = 10_000L
        const val SCROLL_STEP = 8
        val PERMISSIONS = listOf(
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO", "CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV",
            "CONTROL_POWER", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD",
            "READ_INSTALLED_APPS", "READ_RUNNING_APPS", "READ_INPUT_DEVICE_LIST",
            "READ_NETWORK_STATE", "READ_POWER_STATE", "WRITE_NOTIFICATION_TOAST",
        )
    }
}
