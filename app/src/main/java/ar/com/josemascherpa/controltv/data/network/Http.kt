package ar.com.josemascherpa.controltv.data.network

import ar.com.josemascherpa.controltv.domain.error.TvError
import okhttp3.OkHttpClient
import okhttp3.Request

/** La respuesta de un pedido HTTP, ya leída (así no hay que acordarse de cerrarla). */
internal data class HttpResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Hace el pedido fuera del hilo principal. Si no hay red o la TV no contesta, lanza [onError]. */
internal suspend fun OkHttpClient.call(
    request: Request,
    onError: (Exception) -> TvError = { TvError.ConnectionLost(it) },
): HttpResponse = ioCall(onError) {
    newCall(request).execute().use { HttpResponse(it.code, it.body?.string().orEmpty()) }
}

/**
 * Igual que [call] pero devuelve null si falla. Sólo para la búsqueda de TVs: ahí que una IP
 * no conteste es lo normal (la mayoría no son TVs), no un error.
 */
internal suspend fun OkHttpClient.callOrNull(request: Request): HttpResponse? = try {
    call(request)
} catch (e: TvError) {
    null
}
