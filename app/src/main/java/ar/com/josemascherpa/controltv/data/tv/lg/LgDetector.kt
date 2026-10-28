package ar.com.josemascherpa.controltv.data.tv.lg

import ar.com.josemascherpa.controltv.data.discovery.TvDetector
import ar.com.josemascherpa.controltv.data.discovery.isPortOpen
import ar.com.josemascherpa.controltv.domain.model.TvBrand
import ar.com.josemascherpa.controltv.domain.model.TvDevice

/** Las LG con webOS tienen abierto el puerto 3001 (seguro) o 3000. */
class LgDetector : TvDetector {
    override suspend fun detect(ip: String): TvDevice? =
        if (isPortOpen(ip, 3001) || isPortOpen(ip, 3000)) TvDevice(ip, "LG webOS TV", TvBrand.LG) else null
}
