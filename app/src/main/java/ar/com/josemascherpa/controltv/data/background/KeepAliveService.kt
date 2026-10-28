package ar.com.josemascherpa.controltv.data.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ar.com.josemascherpa.controltv.MainActivity
import ar.com.josemascherpa.controltv.R
import ar.com.josemascherpa.controltv.ControlTvApplication

/**
 * Mantiene viva la conexión con la TV cuando la persona deja la app en segundo plano.
 *
 * Un "Service en primer plano" es un servicio que Android no mata porque muestra una
 * notificación fija mientras trabaja. Sin esto, a los pocos segundos de salir de la app Android
 * congela el proceso y se cortan los sockets (las conexiones) con la TV.
 *
 * No habla con la TV: sólo evita que Android la corte. Se enciende cuando hay una TV conectada
 * y se apaga cuando se desconecta ([KeepAliveController]).
 */
class KeepAliveService : Service() {

    // Un "lock" del WiFi evita que el teléfono apague la radio WiFi para ahorrar batería.
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Desde Android 14 hay que declarar para qué se usa el servicio.
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        if ((application as ControlTvApplication).container.session.controller.value == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        acquireWifiLock()
        // Tras morir el proceso se pierde el socket: no tiene sentido reiniciar el servicio solo.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        super.onDestroy()
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Los comandos son breves; no necesitamos el modo de baja latencia de juegos o video.
        @Suppress("DEPRECATION")
        val mode = WifiManager.WIFI_MODE_FULL
        wifiLock = wifi.createWifiLock(mode, "ControlTv:conexion").apply { acquire() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Conexión con la TV", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // Al tocar la notificación se vuelve a abrir la app.
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tv)
            .setContentTitle("ControlTv está conectado")
            .setContentText("Tocá para volver al control remoto.")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "conexion_tv"
        const val NOTIFICATION_ID = 1
    }
}
