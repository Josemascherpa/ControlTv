package ar.com.josemascherpa.controltv.data.tv.roku

import ar.com.josemascherpa.controltv.data.discovery.TvDetector
import ar.com.josemascherpa.controltv.data.discovery.isPortOpen
import ar.com.josemascherpa.controltv.data.network.callOrNull
import ar.com.josemascherpa.controltv.domain.model.TvBrand
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Los Roku responden en http://IP:8060/query/device-info con un XML. Tomamos el nombre
 * que le puso la persona o, si no tiene, el modelo.
 */
class RokuDetector(private val http: OkHttpClient) : TvDetector {

    override suspend fun detect(ip: String): TvDevice? {
        if (!isPortOpen(ip, RokuController.PORT)) return null
        val request = Request.Builder().url("http://$ip:${RokuController.PORT}${RokuController.DEVICE_INFO}").build()
        val xml = http.callOrNull(request)?.body ?: return null
        if ("<device-info>" !in xml) return null
        val name = NAME_TAGS.firstNotNullOfOrNull { tag -> xmlValue(xml, tag) } ?: DEFAULT_NAME
        return TvDevice(ip, name, TvBrand.ROKU)
    }

    private fun xmlValue(xml: String, tag: String): String? =
        Regex("<$tag>([^<]+)</$tag>").find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        val NAME_TAGS = listOf("user-device-name", "friendly-device-name", "model-name")
        const val DEFAULT_NAME = "Roku"
    }
}
