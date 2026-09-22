package com.riomarpescaderia.rutafriaapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

// Un servicio en primer plano no sobrevive un reinicio del celular —
// Android los apaga a todos. Esto lo vuelve a prender solo si el
// vendedor ya tenía la sesión iniciada y el permiso de ubicación dado
// (si le faltara algo de eso, no arranca nada: la próxima vez que abra
// la app a mano queda todo al día, como siempre).
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.haySesion(contexto)) return

        val tienePermiso = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!tienePermiso) return

        val servicio = Intent(contexto, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            contexto.startForegroundService(servicio)
        } else {
            contexto.startService(servicio)
        }
    }
}
