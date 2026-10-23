package com.example.controltv.data.discovery

import com.example.controltv.domain.model.TvDevice
import com.example.controltv.domain.repository.TvDiscovery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Busca TVs en la red WiFi. Toma la IP del teléfono (ej. 192.168.1.37), prueba las 254
 * direcciones vecinas (192.168.1.1 … 192.168.1.254) y le pregunta a cada [TvDetector] si
 * reconoce a su marca. El escáner no sabe de marcas: sólo recorre la red.
 *
 * @param detectors en orden: gana el primero que reconoce la TV.
 */
class NetworkTvScanner(
    private val subnet: LocalSubnet,
    private val detectors: List<TvDetector>,
) : TvDiscovery {

    override suspend fun scan(): List<TvDevice> = coroutineScope {
        val prefix = subnet.prefix() ?: return@coroutineScope emptyList()
        // Semaphore limita cuántas pruebas corren a la vez, para no saturar la red ni el teléfono.
        val limit = Semaphore(MAX_PARALLEL)
        // async lanza cada prueba en paralelo; awaitAll espera a que terminen todas.
        (1..254)
            .map { host -> async { limit.withPermit { probe("$prefix$host") } } }
            .awaitAll()
            .filterNotNull()
    }

    // firstNotNullOfOrNull prueba los detectores en orden y se queda con el primero que no da null.
    override suspend fun probe(ip: String): TvDevice? =
        detectors.firstNotNullOfOrNull { it.detect(ip.trim()) }

    private companion object {
        const val MAX_PARALLEL = 48
    }
}
