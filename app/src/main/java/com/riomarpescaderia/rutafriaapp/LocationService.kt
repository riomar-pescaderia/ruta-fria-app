package com.riomarpescaderia.rutafriaapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

// Servicio en primer plano de tipo "location" (ver AndroidManifest.xml).
// Pide UNA sola posición actual (vía UbicacionHelper) y se apaga solo —
// no hay actualizaciones continuas. Esto es "Localizar ahora": lo
// dispara FcmService al llegar el pedido desde Ruta Fría, casi siempre
// con la app minimizada — por eso, a diferencia de cuando la app está
// abierta, acá SÍ hace falta el permiso de ubicación "todo el tiempo"
// (ACCESS_BACKGROUND_LOCATION) para poder crear este servicio; sin él,
// startForeground() de acá abajo tira una excepción (atajada, para no
// tumbar la app — ver el comentario en el try/catch). El seguimiento
// automático en horario laboral es TrackingService, aparte.
class LocationService : Service() {

    companion object {
        private const val CANAL_ID = "ubicacion"
        private const val NOTIF_ID = 501
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalSiHaceFalta()
        try {
            // Este es justo el punto que puede tirar una excepción y
            // cerrar la app entera si "Localizar ahora" llega por
            // Firebase con la app minimizada y todavía no se dio el
            // permiso de ubicación "todo el tiempo" (ACCESS_BACKGROUND_LOCATION,
            // ver AndroidManifest.xml y MainActivity.kt): Android no deja
            // crear un foreground service de tipo "location" en segundo
            // plano sin ese permiso. Con la app abierta nunca pasa,
            // porque ahí ya alcanza el permiso de ubicación normal.
            startForeground(NOTIF_ID, construirNotificacion())
        } catch (e: Exception) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        UbicacionHelper.capturarYEnviar(this) { stopSelf(startId) }
        return START_NOT_STICKY
    }

    private fun crearCanalSiHaceFalta() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val gestor = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (gestor.getNotificationChannel(CANAL_ID) == null) {
                val canal = NotificationChannel(
                    CANAL_ID,
                    getString(R.string.notif_canal_ubicacion),
                    NotificationManager.IMPORTANCE_LOW
                )
                gestor.createNotificationChannel(canal)
            }
        }
    }

    private fun construirNotificacion(): Notification {
        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle(getString(R.string.notif_titulo_enviando))
            .setContentText(getString(R.string.notif_texto_enviando))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }
}
