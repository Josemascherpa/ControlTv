package com.example.controltv.data.tv.sony

import com.example.controltv.data.network.HttpResponse
import com.example.controltv.data.network.call
import com.example.controltv.domain.controller.HardwareAddress
import com.example.controltv.domain.controller.PowerOn
import com.example.controltv.domain.controller.TextInput
import com.example.controltv.domain.controller.TvController
import com.example.controltv.domain.controller.WebBrowser
import com.example.controltv.domain.controller.scrollWithArrows
import com.example.controltv.domain.error.TvError
import com.example.controltv.domain.model.RemoteKey
import com.example.controltv.domain.model.ScrollDirection
import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.CredentialStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Sony Bravia: "IP Control" por HTTP en el puerto 80. Tiene dos partes:
 *   - /sony/<servicio>: pedidos JSON (escribir texto, abrir el navegador, etc).
 *   - /sony/ircc: manda el mismo código que el control infrarrojo, envuelto en SOAP (XML).
 *
 * Se autentica con una "clave precompartida" (PSK) que la persona elige en la TV:
 *   Ajustes > Red > Configuración de red doméstica > Control IP > Autenticación: "Normal y clave precompartida".
 * La guardamos en [CredentialStore] como la credencial de esta IP.
 *
 * Para prenderla desde el teléfono: Ajustes > Red > Control remoto > "Encendido remoto" activado.
 */
class SonyController(
    override val device: TvDevice,
    private val credentials: CredentialStore,
    private val http: OkHttpClient,
) : TvController, TextInput, WebBrowser, HardwareAddress, PowerOn {

    private val baseUrl = "http://${device.ip}/sony"

    // Nombre del botón → código IRCC. Arrancamos con los conocidos y los reemplazamos
    // por los que informa la propia TV, por si su modelo usa otros.
    private val codes: MutableMap<String, String> = DEFAULT_CODES.toMutableMap()
    private var playing = false

    override suspend fun connect() {
        if (credentials.get(device.ip).isNullOrEmpty()) throw TvError.MissingKey()
        // getSystemInformation pide autenticación: si la clave está mal, falla acá.
        rpc(SERVICE_SYSTEM, "getSystemInformation", onError = { TvError.Unreachable(it) })
        loadRemoteCodes()
    }

    private suspend fun loadRemoteCodes() {
        try {
            val buttons = rpc(SERVICE_SYSTEM, "getRemoteControllerInfo").getJSONArray(1)
            for (i in 0 until buttons.length()) {
                val button = buttons.getJSONObject(i)
                codes[button.getString("name")] = button.getString("value")
            }
        } catch (e: TvError) {
            // Si la TV no da su lista, seguimos con los códigos conocidos.
        } catch (e: JSONException) {
            // Ídem si la lista viene en un formato inesperado.
        }
    }

    override suspend fun sendKey(key: RemoteKey) {
        val name = when (key) {
            RemoteKey.POWER -> "Power"
            RemoteKey.HOME -> "Home"
            RemoteKey.BACK -> "Return"
            RemoteKey.MENU -> "Options"
            RemoteKey.UP -> "Up"
            RemoteKey.DOWN -> "Down"
            RemoteKey.LEFT -> "Left"
            RemoteKey.RIGHT -> "Right"
            RemoteKey.OK -> "Confirm"
            RemoteKey.VOLUME_UP -> "VolumeUp"
            RemoteKey.VOLUME_DOWN -> "VolumeDown"
            RemoteKey.MUTE -> "Mute"
            RemoteKey.CHANNEL_UP -> "ChannelUp"
            RemoteKey.CHANNEL_DOWN -> "ChannelDown"
            // Sony tiene Play y Pausa por separado: los alternamos.
            RemoteKey.PLAY_PAUSE -> if (playing) "Pause" else "Play"
        }
        sendIrcc(codes[name] ?: throw TvError.KeyNotSupported(key))
        if (key == RemoteKey.PLAY_PAUSE) playing = !playing
    }

    // Sólo funciona si en la TV hay un teclado abierto.
    override suspend fun sendText(text: String) {
        rpc(SERVICE_APP_CONTROL, "setTextForm", JSONObject().put("text", text))
    }

    override suspend fun sendEnter() = sendKey(RemoteKey.OK)

    override suspend fun openUrl(url: String) {
        rpc(SERVICE_APP_CONTROL, "setActiveApp", JSONObject().put("uri", "localapp://webappruntime?url=$url"))
    }

    override suspend fun scroll(direction: ScrollDirection) = scrollWithArrows(direction)

    // "setPowerStatus" con true prende la pantalla (y si ya estaba prendida no hace nada).
    override suspend fun turnOn() {
        rpc(SERVICE_SYSTEM, "setPowerStatus", JSONObject().put("status", true))
    }

    // Con "netif" vacío la TV informa todas sus placas de red (WiFi y cable), cada una con su MAC.
    override suspend fun macAddresses(): List<String> {
        val networks = rpc(SERVICE_SYSTEM, "getNetworkSettings", JSONObject().put("netif", "")).optJSONArray(0)
            ?: return emptyList()
        return (0 until networks.length()).mapNotNull { i ->
            networks.optJSONObject(i)?.optString("hwAddr")?.takeIf { it.isNotEmpty() }
        }
    }

    /** Llama a un método JSON de la TV y devuelve su "result". */
    private suspend fun rpc(
        service: String,
        method: String,
        param: JSONObject? = null,
        onError: (Exception) -> TvError = { TvError.ConnectionLost(it) },
    ): JSONArray {
        val body = JSONObject()
            .put("method", method)
            .put("id", 1)
            .put("version", "1.0")
            .put("params", JSONArray().apply { param?.let { put(it) } })
        val response = post("$baseUrl/$service", body.toString(), JSON_TYPE, onError = onError)
        val json = try {
            JSONObject(response.body)
        } catch (e: JSONException) {
            throw TvError.DeviceError(detail = "respuesta inválida")
        }
        // Los errores de los pedidos JSON vienen con código HTTP 200 y un campo "error": [código, texto].
        json.optJSONArray("error")?.let { error ->
            val code = error.optInt(0)
            if (code == HTTP_FORBIDDEN) wrongKey()
            throw TvError.DeviceError(code, error.optString(1))
        }
        return json.optJSONArray("result") ?: JSONArray()
    }

    private suspend fun sendIrcc(code: String) {
        val soap = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1">
                  <IRCCCode>$code</IRCCCode>
                </u:X_SendIRCC>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        post("$baseUrl/ircc", soap, XML_TYPE, "SOAPACTION" to "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
    }

    private suspend fun post(
        url: String,
        body: String,
        type: String,
        vararg headers: Pair<String, String>,
        onError: (Exception) -> TvError = { TvError.ConnectionLost(it) },
    ): HttpResponse {
        val request = Request.Builder()
            .url(url)
            .header("X-Auth-PSK", credentials.get(device.ip).orEmpty())
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(body.toRequestBody(type.toMediaType()))
            .build()
        val response = http.call(request, onError)
        return when {
            response.code == HTTP_FORBIDDEN -> wrongKey()
            !response.isSuccessful -> throw TvError.DeviceError(code = response.code)
            else -> response
        }
    }

    // Borramos la clave guardada para que la próxima vez se la volvamos a pedir a la persona.
    // "Nothing" = esta función nunca termina normalmente: siempre lanza un error.
    private fun wrongKey(): Nothing {
        credentials.clear(device.ip)
        throw TvError.WrongKey()
    }

    override fun disconnect() {}

    private companion object {
        const val SERVICE_SYSTEM = "system"
        const val SERVICE_APP_CONTROL = "appControl"
        const val JSON_TYPE = "application/json; charset=UTF-8"
        const val XML_TYPE = "text/xml; charset=UTF-8"
        const val HTTP_FORBIDDEN = 403

        val DEFAULT_CODES = mapOf(
            "Power" to "AAAAAQAAAAEAAAAVAw==",
            "Home" to "AAAAAQAAAAEAAABgAw==",
            "Return" to "AAAAAgAAAJcAAAAjAw==",
            "Options" to "AAAAAgAAAJcAAAA2Aw==",
            "Up" to "AAAAAQAAAAEAAAB0Aw==",
            "Down" to "AAAAAQAAAAEAAAB1Aw==",
            "Left" to "AAAAAQAAAAEAAAA0Aw==",
            "Right" to "AAAAAQAAAAEAAAAzAw==",
            "Confirm" to "AAAAAQAAAAEAAABlAw==",
            "VolumeUp" to "AAAAAQAAAAEAAAASAw==",
            "VolumeDown" to "AAAAAQAAAAEAAAATAw==",
            "Mute" to "AAAAAQAAAAEAAAAUAw==",
            "ChannelUp" to "AAAAAQAAAAEAAAAQAw==",
            "ChannelDown" to "AAAAAQAAAAEAAAARAw==",
            "Play" to "AAAAAgAAAJcAAAAaAw==",
            "Pause" to "AAAAAgAAAJcAAAAZAw==",
        )
    }
}
