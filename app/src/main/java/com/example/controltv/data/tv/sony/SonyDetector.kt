package com.example.controltv.data.tv.sony

import com.example.controltv.data.discovery.TvDetector
import com.example.controltv.data.discovery.isPortOpen
import com.example.controltv.data.network.callOrNull
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

/**
 * Muchos aparatos tienen el puerto 80 (routers, impresoras), así que le preguntamos a la API
 * de Sony: getInterfaceInformation no pide clave y devuelve "BRAVIA" y el modelo.
 */
class SonyDetector(private val http: OkHttpClient) : TvDetector {

    override suspend fun detect(ip: String): TvDevice? {
        if (!isPortOpen(ip, PORT)) return null
        val body = """{"method":"getInterfaceInformation","id":1,"params":[],"version":"1.0"}"""
        val request = Request.Builder()
            .url("http://$ip/sony/system")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val response = http.callOrNull(request) ?: return null
        val info = try {
            JSONObject(response.body).getJSONArray("result").getJSONObject(0)
        } catch (e: JSONException) {
            return null // tiene el puerto 80 pero no es una Sony
        }
        if (!info.optString("productName").contains("BRAVIA", ignoreCase = true)) return null
        return TvDevice(ip, "Sony ${info.optString("modelName", "Bravia")}", TvBrand.SONY)
    }

    private companion object {
        const val PORT = 80
    }
}
