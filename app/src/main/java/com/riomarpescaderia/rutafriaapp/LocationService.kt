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

// Servicio en primer plano de tipo "location" (ver AndroidManifest.xml):
// mientras está corriendo, con su notificación visible, puede leer el
// GPS sin necesitar el permiso de ubicación "todo el tiempo" — alcanza
// con el permiso normal, porque desde el punto de vista de Android la
// app está "en uso" mientras dura este servicio. Pide UNA sola posición
// actual (vía UbicacionHelper) y se apaga solo — no hay actualizaciones
// continuas. Esto es "Localizar ahora"; el seguimiento automático en
// horario laboral es TrackingService, aparte.
class LocationService : Service() {

    companion object {
        private const val CANAL_ID = "ubicacion"
        private const val NOTIF_ID = 501
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalSiHaceFalta()
        startForeground(NOTIF_ID, construirNotificacion())
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
