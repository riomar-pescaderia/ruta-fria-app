package com.riomarpescaderia.rutafriaapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.concurrent.thread

// Servicio en primer plano de tipo "location" (ver AndroidManifest.xml):
// mientras está corriendo, con su notificación visible, puede leer el
// GPS sin necesitar el permiso de ubicación "todo el tiempo" — alcanza
// con el permiso normal, porque desde el punto de vista de Android la
// app está "en uso" mientras dura este servicio. Pide UNA sola posición
// actual y se apaga solo — no hay actualizaciones continuas.
class LocationService : Service() {

    companion object {
        private const val CANAL_ID = "ubicacion"
        private const val NOTIF_ID = 501
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalSiHaceFalta()
        startForeground(NOTIF_ID, construirNotificacion())

        val tienePermiso = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val token = Prefs.token(applicationContext)

        if (!tienePermiso || token == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        try {
            val cliente = LocationServices.getFusedLocationProviderClient(this)
            val pedido = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .build()
            cliente.getCurrentLocation(pedido, null)
                .addOnSuccessListener { ubicacion ->
                    if (ubicacion == null) {
                        stopSelf(startId)
                        return@addOnSuccessListener
                    }
                    thread {
                        try {
                            ApiClient.subirUbicacion(token, ubicacion.latitude, ubicacion.longitude, ubicacion.accuracy)
                        } catch (e: Exception) {
                            // Sin conexión justo en este momento — cuando
                            // vuelvan a pedir la ubicación se reintenta solo.
                        } finally {
                            stopSelf(startId)
                        }
                    }
                }
                .addOnFailureListener { stopSelf(startId) }
        } catch (e: SecurityException) {
            stopSelf(startId)
        }

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
