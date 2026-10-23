package com.example.controltv.data.tv.googletv

import com.example.controltv.data.discovery.TvDetector
import com.example.controltv.data.discovery.isPortOpen
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice

/**
 * Las Google TV / Android TV con el servicio "Android TV Remote" (viene instalado de fábrica)
 * tienen abierto el puerto 6466. Sin emparejar no podemos saber el modelo, así que va un nombre genérico.
 */
class GoogleTvDetector : TvDetector {
    override suspend fun detect(ip: String): TvDevice? =
        if (isPortOpen(ip, GoogleTvController.REMOTE_PORT)) TvDevice(ip, "Google TV", TvBrand.GOOGLE_TV) else null
}
