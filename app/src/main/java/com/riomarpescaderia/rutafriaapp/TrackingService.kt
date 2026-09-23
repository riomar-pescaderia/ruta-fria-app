package com.riomarpescaderia.rutafriaapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.util.Calendar
import java.util.TimeZone
import kotlin.concurrent.thread

// Seguimiento automático en horario laboral: a diferencia de
// LocationService (una sola posición, a pedido), este servicio se prende
// una vez — al loguearse, al abrir la app, o al reiniciarse el celular
// (ver BootReceiver) — y desde ahí se queda corriendo solo, revisando
// cada tantos minutos (configurable desde Ruta Fría, ver
// Prefs.intervaloMin) si está dentro del horario configurado
// (/vendedores/ubicacion/horario): si lo está, manda la posición; si no,
// no hace nada hasta el próximo control. No usa alarmas del sistema ni
// permisos extra — mientras el servicio esté vivo y en primer plano,
// Android lo sigue tratando como "en uso" para el GPS, igual que
// LocationService.
class TrackingService : Service() {

    companion object {
        private const val CANAL_ID = "seguimiento"
        private const val NOTIF_ID = 502
        private val ZONA_AR = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")

        @Volatile private var corriendo = false
    }

    private val manejador = Handler(Looper.getMainLooper())
    private var proximoTick: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalSiHaceFalta()
        startForeground(NOTIF_ID, construirNotificacion(enHorario = true))

        if (corriendo) {
            // Ya había un ciclo en marcha (por ejemplo, la Activity llamó
            // a start otra vez al volver a primer plano) — no arrancar
            // uno segundo en paralelo.
            return START_STICKY
        }
        corriendo = true
        refrescarConfigYArrancar()
        return START_STICKY
    }

    // Se consulta el horario vigente al servidor una vez, al arrancar el
    // ciclo del día — así si un administrador lo cambió, se usa el nuevo
    // desde el próximo arranque del servicio (no hace falta más en vivo
    // para una app de uso interno como esta).
    private fun refrescarConfigYArrancar() {
        val token = Prefs.token(this)
        if (token == null) {
            detener()
            return
        }
        thread {
            try {
                val respuesta = ApiClient.obtenerConfig(token)
                if (respuesta.exitosa) {
                    val horarios = respuesta.cuerpo.optJSONArray("horarios")
                    if (horarios != null) {
                        Prefs.guardarHorarios(this, horarios.toString())
                    }
                    val intervaloMin = respuesta.cuerpo.optInt("tracking_intervalo_min", -1)
                    if (intervaloMin > 0) {
                        Prefs.guardarIntervaloMin(this, intervaloMin)
                    }
                }
            } catch (e: Exception) {
                // Sin conexión justo al arrancar — se sigue con el último
                // horario que quedó guardado de la vez anterior.
            }
            manejador.post { tick() }
        }
    }

    private fun tick() {
        if (!corriendo) return
        if (!Prefs.haySesion(this)) {
            detener()
            return
        }
        if (enHorarioLaboral()) {
            actualizarNotificacion(enHorario = true)
            UbicacionHelper.capturarYEnviar(this) { programarProximoTick() }
        } else {
            actualizarNotificacion(enHorario = false)
            programarProximoTick()
        }
    }

    private fun programarProximoTick() {
        if (!corriendo) return
        proximoTick?.let { manejador.removeCallbacks(it) }
        val tarea = Runnable { tick() }
        proximoTick = tarea
        val intervaloMs = Prefs.intervaloMin(this).coerceIn(1, 60) * 60 * 1000L
        manejador.postDelayed(tarea, intervaloMs)
    }

    // Recorre las franjas del día de hoy (puede haber más de una — por
    // ejemplo 9 a 13 y 17 a 22) y devuelve true si la hora actual cae
    // adentro de alguna. Calendar.DAY_OF_WEEK va de DOMINGO=1 a SÁBADO=7;
    // se le resta 1 para que coincida con la convención del servidor
    // (0=domingo … 6=sábado, la misma que extract(dow from ...) de Postgres).
    private fun enHorarioLaboral(): Boolean {
        val ahora = Calendar.getInstance(ZONA_AR)
        val diaSemana = ahora.get(Calendar.DAY_OF_WEEK) - 1
        val minutosDelDia = ahora.get(Calendar.HOUR_OF_DAY) * 60 + ahora.get(Calendar.MINUTE)
        return try {
            val horarios = JSONArray(Prefs.horariosJson(this))
            var enFranja = false
            for (i in 0 until horarios.length()) {
                val franja = horarios.getJSONObject(i)
                if (franja.optInt("dia_semana", -1) != diaSemana) continue
                val inicio = franja.optInt("hora_inicio_min", 0)
                val fin = franja.optInt("hora_fin_min", 0)
                if (minutosDelDia in inicio until fin) {
                    enFranja = true
                    break
                }
            }
            enFranja
        } catch (e: Exception) {
            // JSON corrupto o inesperado (no debería pasar) — mejor no
            // mandar ubicación de más que arriesgarse a mandarla de menos
            // por un horario mal interpretado.
            false
        }
    }

    private fun detener() {
        corriendo = false
        proximoTick?.let { manejador.removeCallbacks(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        corriendo = false
        proximoTick?.let { manejador.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun crearCanalSiHaceFalta() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val gestor = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (gestor.getNotificationChannel(CANAL_ID) == null) {
                val canal = NotificationChannel(
                    CANAL_ID,
                    getString(R.string.notif_canal_seguimiento),
                    NotificationManager.IMPORTANCE_LOW
                )
                gestor.createNotificationChannel(canal)
            }
        }
    }

    private fun actualizarNotificacion(enHorario: Boolean) {
        val gestor = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        gestor.notify(NOTIF_ID, construirNotificacion(enHorario))
    }

    private fun construirNotificacion(enHorario: Boolean): Notification {
        val texto = if (enHorario) {
            getString(R.string.notif_texto_seguimiento_activo)
        } else {
            getString(R.string.notif_texto_seguimiento_pausado)
        }
        return NotificationCompat.Builder(this, CANAL_ID)
            .setContentTitle(getString(R.string.notif_titulo_seguimiento))
            .setContentText(texto)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }
}
