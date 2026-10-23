package com.example.controltv.data.network

import com.example.controltv.domain.error.TvError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Abre un WebSocket (una conexión que queda abierta para mandar y recibir mensajes)
 * y espera a que esté listo. Si no se puede abrir, lanza [TvError.Unreachable].
 *
 * OkHttp avisa los eventos con "callbacks" (funciones que llama cuando algo pasa).
 * CompletableDeferred convierte ese callback en algo que podemos esperar con await().
 *
 * Ojo: [onText] y [onClose] se llaman desde un hilo de OkHttp, no desde la corrutina.
 */
internal suspend fun OkHttpClient.openWebSocket(
    url: String,
    onText: (String) -> Unit,
    onClose: (Throwable?) -> Unit = {},
): WebSocket {
    val opened = CompletableDeferred<WebSocket>()
    val socket = newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) = onText(text)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Si falló antes de abrir, se lo pasamos a quien espera; si no, avisamos el corte.
                if (!opened.completeExceptionally(t)) onClose(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onClose(null)
        },
    )
    // withTimeoutOrNull devuelve null si se pasa el tiempo (en vez de lanzar una excepción).
    val ready = try {
        withTimeoutOrNull(OPEN_TIMEOUT_MS) { opened.await() }
    } catch (e: CancellationException) {
        socket.cancel()
        throw e
    } catch (e: Exception) {
        socket.cancel()
        throw TvError.Unreachable(e)
    }
    if (ready == null) {
        socket.cancel()
        throw TvError.Unreachable()
    }
    return ready
}

/** Manda un texto por el socket. Si no hay socket o se cortó, lanza el error correspondiente. */
internal fun WebSocket?.sendOrThrow(text: String) {
    val socket = this ?: throw TvError.NotConnected()
    if (!socket.send(text)) throw TvError.ConnectionLost()
}

private const val OPEN_TIMEOUT_MS = 8_000L
