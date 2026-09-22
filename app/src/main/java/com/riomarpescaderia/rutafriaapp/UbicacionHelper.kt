package com.riomarpescaderia.rutafriaapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.concurrent.thread

// Lo mismo que necesitan LocationService (ubicación a pedido) y
// TrackingService (ubicación automática en horario laboral): pedir UNA
// posición actual del GPS y mandarla al servidor. Vive acá aparte para no
// tener la misma lógica escrita dos veces en los dos servicios.
object UbicacionHelper {

    // "alTerminar" se llama siempre al final (haya salido bien, mal, o no
    // haya podido ni empezar) — así quien lo llama sabe cuándo seguir
    // (por ejemplo, para recién ahí apagarse o programar el próximo
    // intento), sin tener que duplicar el manejo de casos.
    fun capturarYEnviar(contexto: Context, alTerminar: () -> Unit) {
        val tienePermiso = ActivityCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val token = Prefs.token(contexto)

        if (!tienePermiso || token == null) {
            alTerminar()
            return
        }

        try {
            val cliente = LocationServices.getFusedLocationProviderClient(contexto)
            val pedido = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .build()
            cliente.getCurrentLocation(pedido, null)
                .addOnSuccessListener { ubicacion ->
                    if (ubicacion == null) {
                        alTerminar()
                        return@addOnSuccessListener
                    }
                    thread {
                        try {
                            ApiClient.subirUbicacion(token, ubicacion.latitude, ubicacion.longitude, ubicacion.accuracy)
                        } catch (e: Exception) {
                            // Sin conexión justo en este momento — se pierde
                            // este punto puntual, el próximo intento sigue solo.
                        } finally {
                            alTerminar()
                        }
                    }
                }
                .addOnFailureListener { alTerminar() }
        } catch (e: SecurityException) {
            alTerminar()
        }
    }
}
