package com.example.controltv.data.tv.samsung

import com.example.controltv.data.discovery.TvDetector
import com.example.controltv.data.discovery.isPortOpen
import com.example.controltv.data.network.callOrNull
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

/** Las Samsung tienen el puerto 8001 y responden en /api/v2/ con su nombre y modelo. */
class SamsungDetector(private val http: OkHttpClient) : TvDetector {

    override suspend fun detect(ip: String): TvDevice? {
        if (!isPortOpen(ip, PORT)) return null
        val response = http.callOrNull(Request.Builder().url("http://$ip:$PORT/api/v2/").build()) ?: return null
        val name = try {
            JSONObject(response.body).getJSONObject("device").optString("name", DEFAULT_NAME)
        } catch (e: JSONException) {
            return null // tiene el puerto abierto pero no es una Samsung
        }
        return TvDevice(ip, name, TvBrand.SAMSUNG)
    }

    private companion object {
        const val PORT = 8001
        const val DEFAULT_NAME = "Samsung TV"
    }
}
