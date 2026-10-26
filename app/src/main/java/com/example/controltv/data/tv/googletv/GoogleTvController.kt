package com.example.controltv.data.tv.googletv

import com.example.controltv.data.network.call
import com.example.controltv.data.network.ioCall
import com.example.controltv.data.network.logNetworkError
import com.example.controltv.data.tv.googletv.proto.RemoteDirection
import com.example.controltv.data.tv.googletv.proto.RemoteKeyCode
import com.example.controltv.data.tv.googletv.proto.RemoteMessage
import com.example.controltv.data.tv.googletv.proto.RemoteMessageKt
import com.example.controltv.data.tv.googletv.proto.remoteAppLinkLaunchRequest
import com.example.controltv.data.tv.googletv.proto.remoteConfigure
import com.example.controltv.data.tv.googletv.proto.remoteDeviceInfo
import com.example.controltv.data.tv.googletv.proto.remoteEditInfo
import com.example.controltv.data.tv.googletv.proto.remoteImeBatchEdit
import com.example.controltv.data.tv.googletv.proto.remoteImeObject
import com.example.controltv.data.tv.googletv.proto.remoteKeyInject
import com.example.controltv.data.tv.googletv.proto.remoteMessage
import com.example.controltv.data.tv.googletv.proto.remotePingResponse
import com.example.controltv.data.tv.googletv.proto.remoteSetActive
import com.example.controltv.domain.controller.CodePairing
import com.example.controltv.domain.controller.HardwareAddress
import com.example.controltv.domain.controller.PowerOn
import com.example.controltv.domain.controller.TextInput
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.WebBrowser
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Google TV / Android TV con el mismo protocolo que usa la app oficial "Google TV" del teléfono
 * ("Android TV Remote v2"). No necesita el modo desarrollador: la primera vez la TV muestra un
 * código de 6 caracteres y la persona lo escribe en el teléfono.
 *
 *   - Puerto 6467: emparejamiento (ver [GoogleTvPairing]).
 *   - Puerto 6466: el control en sí. Nos presentamos con nuestro certificado; si la TV no lo
 *     conoce, rechaza la conexión y hay que emparejar.
 *
 * Basado en github.com/tronikos/androidtvremote2 (Python, licencia Apache 2.0).
 *
 * La MAC no viene en este protocolo: se la pedimos al Chromecast que traen adentro casi todas
 * las Google TV (http://IP:8008/setup/eureka_info). Si no lo tiene, nos quedamos sin MAC.
 */
class GoogleTvController internal constructor(
    override val device: TvDevice,
    private val identity: ClientIdentity,
    private val http: OkHttpClient,
) : TvController, TextInput, WebBrowser, CodePairing, HardwareAddress, PowerOn {

    // @Volatile: lo usan dos hilos (el que lee mensajes y el que manda botones).
    @Volatile private var channel: TlsChannel? = null
    private var pairing: Pair<TlsChannel, GoogleTvPairing>? = null
    private var reader: CoroutineScope? = null

    // La TV nos dice qué funciones tiene y en qué campo de texto escribir; lo guardamos al recibirlo.
    @Volatile private var activeFeatures = WANTED_FEATURES
    @Volatile private var imeCounter = 0
    @Volatile private var imeFieldCounter = 0

    // La TV avisa (en "remote_start") si la pantalla está prendida o en espera, y vuelve a avisar cuando cambia.
    @Volatile private var screenOn = true

    /**
     * Cuando la TV no conoce nuestro certificado, cada modelo lo dice distinto: algunas mandan un
     * error de seguridad (SSLException), otras (ej. BGH) directamente cortan la conexión, durante el
     * saludo inicial o apenas empezamos a leer. Por eso cualquier corte antes de "remote_start" se
     * toma como "hay que emparejar". Si la TV de verdad no está, el emparejamiento tampoco conecta
     * y se avisa con [TvError.Unreachable], igual que antes.
     */
    override suspend fun connect() {
        disconnect()
        val opened = ioCall(onError = { TvError.Unreachable(it) }) {
            try {
                TlsChannel.open(device.ip, REMOTE_PORT, identity, READ_TIMEOUT_MS)
            } catch (e: IOException) {
                logNetworkError(e)
                null // la TV no nos aceptó (o no está): probamos emparejar
            }
        } ?: return startPairing()

        channel = opened
        val ready = CompletableDeferred<Unit>()
        startReading(opened, ready)
        try {
            withTimeoutOrNull(READY_TIMEOUT_MS) { ready.await() } ?: throw TvError.NoResponse()
        } catch (e: IOException) {
            // Aceptó la conexión y la cortó al leer (SSLException también es un IOException).
            logNetworkError(e)
            disconnect()
            startPairing()
        }
    }

    // Abre el canal de emparejamiento y deja la TV mostrando el código.
    private suspend fun startPairing(): Nothing {
        val pairingChannel = ioCall(onError = { TvError.Unreachable(it) }) {
            TlsChannel.open(device.ip, PAIRING_PORT, identity, PAIRING_TIMEOUT_MS)
        }
        val session = GoogleTvPairing(pairingChannel, identity)
        try {
            ioCall(onError = { TvError.PairingRejected(it) }) { session.start() }
        } catch (e: Throwable) {
            pairingChannel.close()
            throw e
        }
        pairing = pairingChannel to session
        throw TvError.PairingCodeRequired()
    }

    override suspend fun finishPairing(code: String) {
        val (pairingChannel, session) = pairing ?: throw TvError.NotConnected()
        try {
            ioCall(onError = { TvError.PairingRejected(it) }) { session.finish(code) }
        } catch (e: TvError.WrongPairingCode) {
            throw e // el canal sigue abierto: se puede volver a escribir el código
        } catch (e: Throwable) {
            closePairing()
            throw e
        }
        closePairing()
    }

    // Un "lector" en segundo plano: recibe los mensajes de la TV y contesta los que hace falta.
    private fun startReading(opened: TlsChannel, ready: CompletableDeferred<Unit>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        reader = scope
        scope.launch {
            try {
                while (true) {
                    handle(opened.receive(RemoteMessage::parseDelimitedFrom), opened, ready)
                }
            } catch (e: IOException) {
                // Se cortó (o la TV no nos aceptó). Si todavía estábamos conectando, se lo avisamos a connect().
                ready.completeExceptionally(e)
                if (channel === opened) channel = null
                opened.close()
            }
        }
    }

    private fun handle(message: RemoteMessage, opened: TlsChannel, ready: CompletableDeferred<Unit>) {
        when {
            // 1) La TV dice qué sabe hacer; contestamos con lo que queremos usar de eso.
            message.hasRemoteConfigure() -> {
                activeFeatures = WANTED_FEATURES and message.remoteConfigure.code1
                opened.send(
                    remoteMessage {
                        remoteConfigure = remoteConfigure {
                            code1 = activeFeatures
                            deviceInfo = remoteDeviceInfo {
                                unknown1 = 1
                                unknown2 = "1"
                                packageName = PACKAGE_NAME
                                appVersion = APP_VERSION
                            }
                        }
                    },
                )
            }
            message.hasRemoteSetActive() ->
                opened.send(remoteMessage { remoteSetActive = remoteSetActive { active = activeFeatures } })
            // 2) Cuando manda "remote_start", ya se le pueden mandar botones.
            message.hasRemoteStart() -> {
                screenOn = message.remoteStart.started
                ready.complete(Unit)
            }
            // La TV pregunta cada tanto si seguimos ahí; si no contestamos, corta.
            message.hasRemotePingRequest() ->
                opened.send(remoteMessage { remotePingResponse = remotePingResponse { val1 = message.remotePingRequest.val1 } })
            // La TV avisa en qué campo de texto está el foco (ej. el buscador de YouTube) y con qué
            // números hay que identificarlo: sin ellos la TV ignora lo que escribimos. Esos números
            // llegan en estos tres mensajes, así que nos quedamos con los últimos que mandó.
            message.hasRemoteImeKeyInject() -> {
                imeCounter = message.remoteImeKeyInject.appInfo.counter
                imeFieldCounter = message.remoteImeKeyInject.textFieldStatus.counterField
            }
            message.hasRemoteImeShowRequest() ->
                imeFieldCounter = message.remoteImeShowRequest.remoteTextFieldStatus.counterField
            message.hasRemoteImeBatchEdit() -> {
                imeCounter = message.remoteImeBatchEdit.imeCounter
                imeFieldCounter = message.remoteImeBatchEdit.fieldCounter
            }
        }
    }

    override suspend fun sendKey(key: RemoteKey) = press(key.toKeyCode())

    // Escribe en el campo de texto que tenga el foco en la TV (buscador, navegador, etc).
    override suspend fun sendText(text: String) = send {
        remoteImeBatchEdit = remoteImeBatchEdit {
            imeCounter = this@GoogleTvController.imeCounter
            fieldCounter = imeFieldCounter
            editInfo += remoteEditInfo {
                insert = 1
                textFieldStatus = remoteImeObject {
                    start = text.length - 1
                    end = text.length - 1
                    value = text
                }
            }
        }
    }

    override suspend fun sendEnter() = sendKey(RemoteKey.OK)

    // Pide abrir un link: Android elige con qué app (YouTube, el navegador, etc).
    override suspend fun openUrl(url: String) = send {
        remoteAppLinkLaunchRequest = remoteAppLinkLaunchRequest { appLink = url }
    }

    override suspend fun scroll(direction: ScrollDirection) = press(
        when (direction) {
            ScrollDirection.UP -> RemoteKeyCode.KEYCODE_PAGE_UP
            ScrollDirection.DOWN -> RemoteKeyCode.KEYCODE_PAGE_DOWN
            ScrollDirection.LEFT -> RemoteKeyCode.KEYCODE_DPAD_LEFT
            ScrollDirection.RIGHT -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
        },
    )

    // En espera la TV sigue atendiendo el control por la red: si la pantalla está apagada, "apretamos" encender.
    override suspend fun turnOn() {
        if (!screenOn) press(RemoteKeyCode.KEYCODE_POWER)
    }

    override suspend fun macAddresses(): List<String> {
        val request = Request.Builder().url("http://${device.ip}:$CAST_PORT/setup/eureka_info?params=device_info").build()
        val info = try {
            JSONObject(http.call(request).body)
        } catch (e: JSONException) {
            return emptyList() // no tiene Chromecast adentro (o contestó otra cosa): seguimos sin MAC
        }
        // Según la versión, la MAC viene suelta o adentro de "device_info".
        val mac = info.optString("mac_address").ifEmpty { info.optJSONObject("device_info")?.optString("mac_address").orEmpty() }
        return listOfNotNull(mac.takeIf { it.isNotEmpty() })
    }

    // Un toque corto de un botón ("SHORT"; también existe mantenerlo apretado).
    private suspend fun press(code: RemoteKeyCode) = send {
        remoteKeyInject = remoteKeyInject {
            keyCode = code
            direction = RemoteDirection.SHORT
        }
    }

    private suspend fun send(build: RemoteMessageKt.Dsl.() -> Unit) {
        val current = channel ?: throw TvError.NotConnected()
        ioCall { current.send(remoteMessage(build)) }
    }

    private fun RemoteKey.toKeyCode(): RemoteKeyCode = when (this) {
        RemoteKey.POWER -> RemoteKeyCode.KEYCODE_POWER
        RemoteKey.HOME -> RemoteKeyCode.KEYCODE_HOME
        RemoteKey.BACK -> RemoteKeyCode.KEYCODE_BACK
        RemoteKey.MENU -> RemoteKeyCode.KEYCODE_MENU
        RemoteKey.UP -> RemoteKeyCode.KEYCODE_DPAD_UP
        RemoteKey.DOWN -> RemoteKeyCode.KEYCODE_DPAD_DOWN
        RemoteKey.LEFT -> RemoteKeyCode.KEYCODE_DPAD_LEFT
        RemoteKey.RIGHT -> RemoteKeyCode.KEYCODE_DPAD_RIGHT
        RemoteKey.OK -> RemoteKeyCode.KEYCODE_DPAD_CENTER
        RemoteKey.VOLUME_UP -> RemoteKeyCode.KEYCODE_VOLUME_UP
        RemoteKey.VOLUME_DOWN -> RemoteKeyCode.KEYCODE_VOLUME_DOWN
        RemoteKey.MUTE -> RemoteKeyCode.KEYCODE_VOLUME_MUTE
        RemoteKey.CHANNEL_UP -> RemoteKeyCode.KEYCODE_CHANNEL_UP
        RemoteKey.CHANNEL_DOWN -> RemoteKeyCode.KEYCODE_CHANNEL_DOWN
        RemoteKey.PLAY_PAUSE -> RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE
    }

    private fun closePairing() {
        pairing?.first?.close()
        pairing = null
    }

    override fun disconnect() {
        reader?.cancel()
        reader = null
        channel?.close()
        channel = null
        closePairing()
    }

    companion object {
        const val REMOTE_PORT = 6466
        const val PAIRING_PORT = 6467
        private const val CAST_PORT = 8008

        private const val PACKAGE_NAME = "atvremote"
        private const val APP_VERSION = "1.0.0"
        private const val READY_TIMEOUT_MS = 10_000L
        private const val PAIRING_TIMEOUT_MS = 10_000
        // La TV manda un "ping" cada pocos segundos: si en este tiempo no llega nada, se cortó.
        private const val READ_TIMEOUT_MS = 20_000

        // Funciones del protocolo que usamos (cada una es un bit): ping, botones, teclado,
        // encendido, volumen y abrir links.
        private const val FEATURE_PING = 1
        private const val FEATURE_KEY = 2
        private const val FEATURE_IME = 4
        private const val FEATURE_POWER = 32
        private const val FEATURE_VOLUME = 64
        private const val FEATURE_APP_LINK = 512
        private const val WANTED_FEATURES =
            FEATURE_PING or FEATURE_KEY or FEATURE_IME or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK
    }
}
