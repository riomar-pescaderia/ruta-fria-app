package com.riomarpescaderia.rutafriaapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// Lo mismo que necesitan LocationService (ubicación a pedido) y
// TrackingService (ubicación automática en horario laboral): pedir UNA
// posición actual del GPS y mandarla al servidor. Vive acá aparte para no
// tener la misma lógica escrita dos veces en los dos servicios.
object UbicacionHelper {

    // Si en este tiempo Play Services no consiguió una posición (GPS
    // débil, adentro de un local, arranque en frío del chip GPS) se da
    // por perdido este intento y se sigue con el próximo ciclo. Antes el
    // pedido no tenía ningún límite (Play Services por defecto puede
    // esperar indefinidamente) y esa era la causa de que el seguimiento
    // automático se quedara trabado para siempre: el ciclo no podía
    // programar el próximo control porque este pedido nunca terminaba de
    // responder ni con éxito ni con error.
    private const val TIMEOUT_UBICACION_MS = 25_000L

    // Red de seguridad aparte del límite de arriba: si por lo que sea
    // Play Services no respetara su propio límite (versión vieja o rota
    // en algún celular en particular), esto asegura que "alTerminar" se
    // llame igual pasado este tiempo, y el ciclo de seguimiento nunca
    // quede colgado para siempre.
    private const val TIMEOUT_RED_SEGURIDAD_MS = 30_000L

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

        // Puede haber dos caminos que terminen llamando a "alTerminar"
        // (la respuesta real de Play Services, o la red de seguridad de
        // acá abajo) — este flag asegura que se llame una sola vez, gane
        // el que gane.
        val yaTermino = AtomicBoolean(false)
        val manejador = Handler(Looper.getMainLooper())
        fun terminarUnaVez() {
            if (yaTermino.compareAndSet(false, true)) alTerminar()
        }
        val redSeguridad = Runnable { terminarUnaVez() }
        manejador.postDelayed(redSeguridad, TIMEOUT_RED_SEGURIDAD_MS)

        try {
            val cliente = LocationServices.getFusedLocationProviderClient(contexto)
            val pedido = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .setDurationMillis(TIMEOUT_UBICACION_MS)
                .build()
            cliente.getCurrentLocation(pedido, null)
                .addOnSuccessListener { ubicacion ->
                    manejador.removeCallbacks(redSeguridad)
                    if (ubicacion == null) {
                        terminarUnaVez()
                        return@addOnSuccessListener
                    }
                    thread {
                        try {
                            ApiClient.subirUbicacion(token, ubicacion.latitude, ubicacion.longitude, ubicacion.accuracy)
                        } catch (e: Exception) {
                            // Sin conexión justo en este momento — se pierde
                            // este punto puntual, el próximo intento sigue solo.
                        } finally {
                            terminarUnaVez()
                        }
                    }
                }
                .addOnFailureListener {
                    manejador.removeCallbacks(redSeguridad)
                    terminarUnaVez()
                }
        } catch (e: Exception) {
            // Antes acá solo se atajaba SecurityException (falta de
            // permiso) — pero Play Services puede tirar otro tipo de
            // error si, por ejemplo, la ubicación del celular está
            // desactivada del todo o el dispositivo tiene Play Services
            // desactualizado o roto. Sin atajar cualquier excepción acá,
            // ese error no se manejaba y terminaba cerrando la app entera
            // (el cartel de "Ruta Fría continúa fallando").
            manejador.removeCallbacks(redSeguridad)
            terminarUnaVez()
        }
    }
}
