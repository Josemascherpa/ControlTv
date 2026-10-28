package ar.com.josemascherpa.controltv.data.discovery

import android.util.Log
import ar.com.josemascherpa.controltv.domain.model.ScanResult
import ar.com.josemascherpa.controltv.domain.model.TvDevice
import ar.com.josemascherpa.controltv.domain.repository.TvDiscovery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Combina los anuncios de la red con un barrido acotado según la máscara del WiFi.
 * Le pregunta a cada [TvDetector] si reconoce una TV. El escáner no conoce marcas.
 *
 * @param detectors en orden: gana el primero que reconoce la TV.
 */
class NetworkTvScanner(
    private val subnet: LocalSubnet,
    private val detectors: List<TvDetector>,
    private val announcedAddresses: suspend () -> List<String> = { emptyList() },
) : TvDiscovery {

    override suspend fun scan(knownIps: List<String>): ScanResult = coroutineScope {
        val prefix = subnet.prefix()
        if (prefix == null) return@coroutineScope ScanResult(null, emptyList())
        val announcements = async { announcedAddresses() }
        // Las conocidas van primero, así son las primeras en probarse.
        val ips = (knownIps + subnet.hosts()).distinct()
        // Semaphore limita cuántas pruebas corren a la vez, para no saturar la red ni el teléfono.
        val limit = Semaphore(MAX_PARALLEL)
        // async lanza cada prueba en paralelo; awaitAll espera a que terminen todas.
        val sweep = async {
            ips.map { ip -> async { limit.withPermit { probe(ip) } } }
                .awaitAll()
                .filterNotNull()
        }
        val advertised = announcements.await().filterNot { it in ips }
            .map { ip -> async { limit.withPermit { probe(ip) } } }.awaitAll().filterNotNull()
        val found = (sweep.await() + advertised).distinctBy { it.ip }
        // Queda anotado en el registro del sistema: se ve con `adb logcat -s ControlTv`.
        Log.i(LOG_TAG, "Búsqueda en la red ${prefix}x: ${ips.size} IPs, ${found.size} TVs: ${found.map { it.ip }}")
        ScanResult(prefix, found)
    }

    // Los detectores se prueban a la vez (cada uno espera hasta 1,5 s si la TV tarda en contestar;
    // uno atrás del otro sería muy lento). Después, firstNotNullOfOrNull respeta el orden de la
    // lista: gana el primero que reconoció la TV, aunque otro haya contestado antes.
    override suspend fun probe(ip: String): TvDevice? = coroutineScope {
        detectors
            .map { detector -> async { detector.detect(ip.trim()) } }
            .awaitAll()
            .firstNotNullOfOrNull { it }
    }

    private companion object {
        const val MAX_PARALLEL = 48
        const val LOG_TAG = "ControlTv"
    }
}
