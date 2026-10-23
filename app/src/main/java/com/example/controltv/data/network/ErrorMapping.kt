package com.example.controltv.data.network

import com.example.controltv.domain.error.TvError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Corre [block] en Dispatchers.IO (hilos pensados para red/disco, separados de los de la pantalla)
 * y convierte cualquier error técnico en un [TvError] con [onError].
 *
 * Así ninguna excepción de librerías (IOException, errores de ADB, JSON…) sale de la capa "data".
 * La cancelación se deja pasar tal cual: atraparla rompería cómo se cancelan las corrutinas.
 */
internal suspend fun <T> ioCall(
    onError: (Exception) -> TvError = { TvError.ConnectionLost(it) },
    block: suspend () -> T,
): T = withContext(Dispatchers.IO) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: TvError) {
        throw e
    } catch (e: Exception) {
        throw onError(e)
    }
}
