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
}
