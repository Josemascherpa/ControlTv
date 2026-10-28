package ar.com.josemascherpa.controltv.data.tv.android

import ar.com.josemascherpa.controltv.data.discovery.TvDetector
import ar.com.josemascherpa.controltv.data.discovery.isPortOpen
import ar.com.josemascherpa.controltv.domain.model.TvBrand
import ar.com.josemascherpa.controltv.domain.model.TvDevice

/**
 * Android TV, Google TV y Fire TV con la depuración (ADB) activada tienen abierto el 5555.
 * Sin abrir ADB no podemos saber el modelo (pediría permiso en la TV), así que va un nombre genérico.
 */
class AndroidTvDetector : TvDetector {
    override suspend fun detect(ip: String): TvDevice? =
        if (isPortOpen(ip, AndroidTvController.ADB_PORT)) TvDevice(ip, "Google / Android TV", TvBrand.ANDROID) else null
}
