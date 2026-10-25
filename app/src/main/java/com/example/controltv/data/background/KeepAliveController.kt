package com.example.controltv.data.background

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.controltv.domain.session.TvSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Enciende el [KeepAliveService] cuando hay una TV conectada y lo apaga cuando se desconecta.
 * Así la lógica de la app no necesita saber que existe un servicio de Android.
 */
class KeepAliveController(private val context: Context, session: TvSession) {

    // Este scope vive lo mismo que la app. Main = el hilo principal, donde se pueden manejar servicios.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        session.controller
            .map { it != null }
            .distinctUntilChanged()
            .onEach { connected -> if (connected) start() else stop() }
            .launchIn(scope)
    }

    private fun start() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
        } catch (e: IllegalStateException) {
            // Android no deja arrancar un servicio en primer plano si la app ya está en segundo plano.
            // Pasa sólo si la conexión se completa justo después de salir; no es grave: sin servicio
            // la app funciona igual mientras esté abierta.
        }
    }

    private fun stop() {
        context.stopService(Intent(context, KeepAliveService::class.java))
    }
}
