package com.example.controltv.data.tv.lg

import com.example.controltv.data.discovery.TvDetector
import com.example.controltv.data.discovery.isPortOpen
import com.example.controltv.domain.model.TvBrand
import com.example.controltv.domain.model.TvDevice

/** Las LG con webOS tienen abierto el puerto 3001 (seguro) o 3000. */
class LgDetector : TvDetector {
    override suspend fun detect(ip: String): TvDevice? =
        if (isPortOpen(ip, 3001) || isPortOpen(ip, 3000)) TvDevice(ip, "LG webOS TV", TvBrand.LG) else null
}
