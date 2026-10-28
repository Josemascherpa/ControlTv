package ar.com.josemascherpa.controltv.data.tv.roku

import android.net.Uri
import android.util.Xml
import ar.com.josemascherpa.controltv.data.network.HttpResponse
import ar.com.josemascherpa.controltv.data.network.call
import ar.com.josemascherpa.controltv.domain.controller.ContentSearch
import ar.com.josemascherpa.controltv.domain.controller.HardwareAddress
import ar.com.josemascherpa.controltv.domain.controller.PowerOn
import ar.com.josemascherpa.controltv.domain.controller.TextInput
import ar.com.josemascherpa.controltv.domain.controller.TvController
import ar.com.josemascherpa.controltv.domain.error.TvError
import ar.com.josemascherpa.controltv.domain.model.RemoteKey
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader

/**
 * Roku (Roku TV de TCL, Hisense, onn, Philco, etc. y los Roku Express/Stick):
 * "External Control Protocol" (ECP). Es HTTP simple en el puerto 8060, sin emparejar:
 *   POST http://IP:8060/keypress/Home   → aprieta "Inicio"
 *   POST http://IP:8060/keypress/Lit_a  → escribe la letra "a"
 *
 * Roku no tiene navegador, por eso implementa ContentSearch (su buscador) y no WebBrowser.
 *
 * Requisito (Roku OS 14.1+): en la TV, Ajustes > Sistema > Configuración avanzada >
 * Control por apps móviles > Acceso a la red: "Habilitado".
 *
 * Para prenderla desde el teléfono: Ajustes > Sistema > Energía > "Inicio rápido de TV" activado.
 * Así en espera sigue atendiendo la red y entiende "PowerOn".
 */
class RokuController(
    override val device: TvDevice,
    private val http: OkHttpClient,
) : TvController, TextInput, ContentSearch, HardwareAddress, PowerOn {

    private val baseUrl = "http://${device.ip}:$PORT"

    // ECP no mantiene una conexión abierta: "conectar" es sólo comprobar que responde.
    override suspend fun connect() {
        get(DEVICE_INFO, onError = { TvError.Unreachable(it) })
    }

    override suspend fun sendKey(key: RemoteKey) {
        if (key == RemoteKey.POWER) {
            // "Power" no está documentado; PowerOn/PowerOff sí. Vemos en qué estado está y lo invertimos.
            val isOn = "<power-mode>PowerOn</power-mode>" in get(DEVICE_INFO).body
            post("/keypress/${if (isOn) "PowerOff" else "PowerOn"}")
        } else {
            post("/keypress/${keyName(key)}")
        }
    }

    // Roku no tiene "escribir texto": se manda letra por letra con Lit_ (codificada para la URL).
    override suspend fun sendText(text: String) {
        text.forEach { post("/keypress/Lit_${Uri.encode(it.toString())}") }
    }

    override suspend fun sendEnter() = post("/keypress/Enter")

    // Abre el buscador del Roku con el texto (busca en todas las apps instaladas).
    override suspend fun search(query: String) = post("/search/browse?keyword=${Uri.encode(query)}&launch=true")

    override suspend fun turnOn() = post("/keypress/PowerOn")

    override suspend fun macAddresses(): List<String> {
        val info = deviceInfo(get(DEVICE_INFO).body)
        return listOfNotNull(info["wifi-mac"], info["ethernet-mac"]).filter { it.isNotEmpty() }
    }

    // device-info es un XML con un dato por etiqueta (<wifi-mac>…</wifi-mac>). Lo leemos con el
    // lector de XML que trae Android y lo devolvemos como mapa etiqueta → texto.
    private fun deviceInfo(xml: String): Map<String, String> = try {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        buildMap {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    val tag = parser.name
                    if (parser.next() == XmlPullParser.TEXT) put(tag, parser.text.trim())
                }
            }
        }
    } catch (e: XmlPullParserException) {
        throw TvError.DeviceError(detail = "datos de la TV inválidos")
    } catch (e: IOException) {
        throw TvError.DeviceError(detail = "datos de la TV inválidos")
    }

    private fun keyName(key: RemoteKey): String = when (key) {
        RemoteKey.POWER -> "PowerOff"
        RemoteKey.HOME -> "Home"
        RemoteKey.BACK -> "Back"
        RemoteKey.MENU -> "Info" // el botón ✱ del control de Roku
        RemoteKey.UP -> "Up"
        RemoteKey.DOWN -> "Down"
        RemoteKey.LEFT -> "Left"
        RemoteKey.RIGHT -> "Right"
        RemoteKey.OK -> "Select"
        RemoteKey.VOLUME_UP -> "VolumeUp"
        RemoteKey.VOLUME_DOWN -> "VolumeDown"
        RemoteKey.MUTE -> "VolumeMute"
        RemoteKey.CHANNEL_UP -> "ChannelUp"
        RemoteKey.CHANNEL_DOWN -> "ChannelDown"
        RemoteKey.PLAY_PAUSE -> "Play" // en Roku "Play" funciona como play/pausa
    }

    private suspend fun get(
        path: String,
        onError: (Exception) -> TvError = { TvError.ConnectionLost(it) },
    ): HttpResponse = send(Request.Builder().url(baseUrl + path).build(), onError)

    private suspend fun post(path: String) {
        send(Request.Builder().url(baseUrl + path).post(ByteArray(0).toRequestBody()).build())
    }

    private suspend fun send(
        request: Request,
        onError: (Exception) -> TvError = { TvError.ConnectionLost(it) },
    ): HttpResponse {
        val response = http.call(request, onError)
        return when {
            response.code == 403 -> throw TvError.RemoteControlDisabled()
            !response.isSuccessful -> throw TvError.DeviceError(code = response.code)
            else -> response
        }
    }

    override fun disconnect() {}

    companion object {
        const val PORT = 8060
        const val DEVICE_INFO = "/query/device-info"
    }
}
