package com.riomarpescaderia.rutafriaapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Guarda el token de sesión del celular (el que devuelve /api/app/login)
// y algunos datos livianos del usuario logueado. Es una SharedPreferences
// común, no cifrada — para una app interna de uso puntual alcanza; si en
// algún momento hace falta más seguridad, se puede cambiar por
// EncryptedSharedPreferences sin tocar el resto de la app.
object Prefs {
    private const val ARCHIVO = "ruta_fria_prefs"
    private const val CLAVE_TOKEN = "token"
    private const val CLAVE_USUARIO_ID = "usuario_id"
    private const val CLAVE_USUARIO_NOMBRE = "usuario_nombre"
    private const val CLAVE_HORARIOS_JSON = "tracking_horarios_json"
    private const val CLAVE_INTERVALO_MIN = "tracking_intervalo_min"

    // Cada cuántos minutos se manda la posición mientras se está en una
    // franja activa — editable desde Ruta Fría (/vendedores/ubicacion/horario),
    // ya no es un número fijo en el código de la app. 5 minutos como
    // valor de arranque conservador (poca batería) por si todavía no se
    // pudo consultar la configuración al servidor ninguna vez.
    private const val DEFECTO_INTERVALO_MIN = 5

    // SharedPreferences no guarda listas, así que el horario de
    // seguimiento (una franja por día de la semana, puede haber varias
    // por día) se guarda tal cual llega de GET /api/app/config: un texto
    // JSON con la forma [{"dia_semana":1,"hora_inicio_min":480,"hora_fin_min":1140}, ...].
    // Por defecto, si todavía no se pudo consultar al servidor ninguna
    // vez, se arranca con 08:00 a 19:00 los 7 días.
    private fun horariosDefectoJson(): String {
        val arr = JSONArray()
        for (dia in 0..6) {
            arr.put(JSONObject().put("dia_semana", dia).put("hora_inicio_min", 480).put("hora_fin_min", 1140))
        }
        return arr.toString()
    }

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun guardarSesion(contexto: Context, token: String, usuarioId: Int, nombre: String) {
        prefs(contexto).edit()
            .putString(CLAVE_TOKEN, token)
            .putInt(CLAVE_USUARIO_ID, usuarioId)
            .putString(CLAVE_USUARIO_NOMBRE, nombre)
            .apply()
    }

    fun token(contexto: Context): String? = prefs(contexto).getString(CLAVE_TOKEN, null)

    fun nombre(contexto: Context): String? = prefs(contexto).getString(CLAVE_USUARIO_NOMBRE, null)

    fun haySesion(contexto: Context): Boolean = token(contexto) != null

    fun cerrarSesion(contexto: Context) {
        prefs(contexto).edit().clear().apply()
    }

    fun guardarHorarios(contexto: Context, horariosJson: String) {
        prefs(contexto).edit().putString(CLAVE_HORARIOS_JSON, horariosJson).apply()
    }

    fun horariosJson(contexto: Context): String =
        prefs(contexto).getString(CLAVE_HORARIOS_JSON, null) ?: horariosDefectoJson()

    fun guardarIntervaloMin(contexto: Context, intervaloMin: Int) {
        prefs(contexto).edit().putInt(CLAVE_INTERVALO_MIN, intervaloMin).apply()
    }

    fun intervaloMin(contexto: Context): Int =
        prefs(contexto).getInt(CLAVE_INTERVALO_MIN, DEFECTO_INTERVALO_MIN)
}
