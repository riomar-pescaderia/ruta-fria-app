package com.riomarpescaderia.rutafriaapp

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// Todas las llamadas acá son bloqueantes (no usan corrutinas para no
// sumar otra dependencia) — quien las use tiene que hacerlo desde un
// hilo que no sea el principal (ver kotlin.concurrent.thread en
// MainActivity, FcmService y LocationService).
object ApiClient {

    // Si el día de mañana Ruta Fría se muda de Render a otro lugar,
    // alcanza con cambiar esta constante y volver a compilar la app.
    const val BASE_URL = "https://ruta-fria.onrender.com"

    data class Respuesta(val exitosa: Boolean, val codigo: Int, val cuerpo: JSONObject)

    private fun llamar(path: String, metodo: String, cuerpo: JSONObject?, token: String?): Respuesta {
        val url = URL(BASE_URL + path)
        val conexion = url.openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = metodo
            conexion.connectTimeout = 15000
            conexion.readTimeout = 20000
            conexion.setRequestProperty("Content-Type", "application/json")
            conexion.setRequestProperty("Accept", "application/json")
            if (token != null) conexion.setRequestProperty("Authorization", "Bearer $token")
            if (cuerpo != null) {
                conexion.doOutput = true
                OutputStreamWriter(conexion.outputStream, Charsets.UTF_8).use { it.write(cuerpo.toString()) }
            }
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText) ?: "{}"
            val json = try {
                JSONObject(texto)
            } catch (e: Exception) {
                JSONObject()
            }
            return Respuesta(codigo in 200..299, codigo, json)
        } finally {
            conexion.disconnect()
        }
    }

    fun login(usuario: String, password: String, modelo: String): Respuesta {
        val cuerpo = JSONObject()
            .put("username", usuario)
            .put("password", password)
            .put("modelo", modelo)
        return llamar("/api/app/login", "POST", cuerpo, null)
    }

    fun registrarTokenFcm(token: String, tokenFcm: String): Respuesta {
        val cuerpo = JSONObject().put("token_fcm", tokenFcm)
        return llamar("/api/app/dispositivo/fcm", "POST", cuerpo, token)
    }

    fun subirUbicacion(token: String, lat: Double, lng: Double, precision: Float): Respuesta {
        val cuerpo = JSONObject()
            .put("lat", lat)
            .put("lng", lng)
            .put("precision", precision.toDouble())
        return llamar("/api/app/ubicacion", "POST", cuerpo, token)
    }

    // Entre qué horas (minutos desde la medianoche, hora de Argentina) la
    // app tiene permitido mandar ubicación sola — lo define un
    // administrador desde Ruta Fría (/vendedores/ubicacion/horario). Lo
    // consulta TrackingService cada vez que arranca, para no quedarse
    // con un horario viejo si lo cambiaron.
    fun obtenerConfig(token: String): Respuesta {
        return llamar("/api/app/config", "GET", null, token)
    }
}
