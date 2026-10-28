package ar.com.josemascherpa.controltv.data.network

import android.util.Log
import ar.com.josemascherpa.controltv.domain.error.TvError
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
        logNetworkError(e)
        throw onError(e)
    }
}

/**
 * Anota el error técnico real en el registro del sistema ("logcat"). La persona sólo ve el mensaje
 * simple, pero así quien programa puede ver qué pasó con `adb logcat -s ControlTv`.
 */
internal fun logNetworkError(e: Exception) {
    Log.w(LOG_TAG, "Error de red", e)
}

private const val LOG_TAG = "ControlTv"
