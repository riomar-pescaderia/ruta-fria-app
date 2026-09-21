package com.riomarpescaderia.rutafriaapp

import android.content.Intent
import android.os.Build
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.concurrent.thread

// Recibe los mensajes que manda Firebase Cloud Messaging. Solo entendemos
// un tipo de mensaje: "solicitar_ubicacion", el que dispara Ruta Fría
// cuando un administrador toca "Localizar ahora". No hay ningún otro
// disparador de ubicación en la app — ni periódico ni continuo.
class FcmService : FirebaseMessagingService() {

    // Firebase renueva el token del dispositivo de vez en cuando (no es
    // fijo para siempre) — cada vez que pasa, hay que avisarle al
    // servidor, si no las notificaciones futuras dejarían de llegar.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val sesionToken = Prefs.token(applicationContext) ?: return
        thread {
            try {
                ApiClient.registrarTokenFcm(sesionToken, token)
            } catch (e: Exception) {
                // Sin conexión en este momento — el próximo onNewToken
                // (o el próximo login) lo vuelve a intentar.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data["tipo"] != "solicitar_ubicacion") return
        if (!Prefs.haySesion(applicationContext)) return

        val intent = Intent(applicationContext, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
    }
}
