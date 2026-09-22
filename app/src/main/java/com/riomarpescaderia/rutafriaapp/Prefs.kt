package com.riomarpescaderia.rutafriaapp

import android.content.Context

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
    private const val CLAVE_TRACKING_INICIO_MIN = "tracking_inicio_min"
    private const val CLAVE_TRACKING_FIN_MIN = "tracking_fin_min"

    // Minutos desde la medianoche, mismo formato que usa el servidor (ver
    // GET /api/app/config) — 08:00 y 19:00 como valor de arranque, por si
    // todavía no se pudo consultar al servidor ninguna vez.
    private const val DEFECTO_INICIO_MIN = 480
    private const val DEFECTO_FIN_MIN = 1140

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

    fun guardarHorarioTracking(contexto: Context, horaInicioMin: Int, horaFinMin: Int) {
        prefs(contexto).edit()
            .putInt(CLAVE_TRACKING_INICIO_MIN, horaInicioMin)
            .putInt(CLAVE_TRACKING_FIN_MIN, horaFinMin)
            .apply()
    }

    fun horaInicioTrackingMin(contexto: Context): Int =
        prefs(contexto).getInt(CLAVE_TRACKING_INICIO_MIN, DEFECTO_INICIO_MIN)

    fun horaFinTrackingMin(contexto: Context): Int =
        prefs(contexto).getInt(CLAVE_TRACKING_FIN_MIN, DEFECTO_FIN_MIN)
}
