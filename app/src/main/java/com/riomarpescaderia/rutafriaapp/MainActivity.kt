package com.riomarpescaderia.rutafriaapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.concurrent.thread

// Pantalla única: login, y una vez logueado, el estado de los permisos
// que la app necesita. No hace falta dejar esta pantalla abierta para
// que funcione la localización a pedido — solo hace falta haber
// iniciado sesión una vez y haber dado los permisos una vez.
class MainActivity : AppCompatActivity() {

    private lateinit var grupoLogin: android.view.View
    private lateinit var grupoSesion: android.view.View
    private lateinit var campoUsuario: EditText
    private lateinit var campoPassword: EditText
    private lateinit var botonIngresar: Button
    private lateinit var textoErrorLogin: TextView
    private lateinit var textoSaludo: TextView
    private lateinit var textoEstado: TextView
    private lateinit var botonPermisoUbicacion: Button
    private lateinit var botonPermisoNotificaciones: Button
    private lateinit var botonPermisoBateria: Button
    private lateinit var botonCerrarSesion: Button

    private val pedirUbicacion = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        actualizarEstadoPermisos()
    }
    private val pedirNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        actualizarEstadoPermisos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grupoLogin = findViewById(R.id.grupoLogin)
        grupoSesion = findViewById(R.id.grupoSesion)
        campoUsuario = findViewById(R.id.campoUsuario)
        campoPassword = findViewById(R.id.campoPassword)
        botonIngresar = findViewById(R.id.botonIngresar)
        textoErrorLogin = findViewById(R.id.textoErrorLogin)
        textoSaludo = findViewById(R.id.textoSaludo)
        textoEstado = findViewById(R.id.textoEstado)
        botonPermisoUbicacion = findViewById(R.id.botonPermisoUbicacion)
        botonPermisoNotificaciones = findViewById(R.id.botonPermisoNotificaciones)
        botonPermisoBateria = findViewById(R.id.botonPermisoBateria)
        botonCerrarSesion = findViewById(R.id.botonCerrarSesion)

        botonIngresar.setOnClickListener { intentarLogin() }
        botonPermisoUbicacion.setOnClickListener {
            pedirUbicacion.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        botonPermisoNotificaciones.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        botonPermisoBateria.setOnClickListener { pedirExencionBateria() }
        botonCerrarSesion.setOnClickListener {
            detenerSeguimiento()
            Prefs.cerrarSesion(this)
            mostrarPantallaLogin()
        }

        if (Prefs.haySesion(this)) {
            mostrarPantallaSesion()
        } else {
            mostrarPantallaLogin()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.haySesion(this)) actualizarEstadoPermisos()
    }

    private fun mostrarPantallaLogin() {
        grupoLogin.visibility = android.view.View.VISIBLE
        grupoSesion.visibility = android.view.View.GONE
    }

    private fun mostrarPantallaSesion() {
        grupoLogin.visibility = android.view.View.GONE
        grupoSesion.visibility = android.view.View.VISIBLE
        textoSaludo.text = getString(R.string.sesion_saludo, Prefs.nombre(this) ?: "")
        actualizarEstadoPermisos()
    }

    private fun intentarLogin() {
        val usuario = campoUsuario.text.toString().trim()
        val password = campoPassword.text.toString()
        if (usuario.isEmpty() || password.isEmpty()) return

        textoErrorLogin.visibility = android.view.View.GONE
        botonIngresar.isEnabled = false
        botonIngresar.text = "Ingresando…"

        thread {
            try {
                val modelo = "${Build.MANUFACTURER} ${Build.MODEL}"
                val respuesta = ApiClient.login(usuario, password, modelo)
                runOnUiThread {
                    botonIngresar.isEnabled = true
                    botonIngresar.setText(R.string.boton_ingresar)
                    if (respuesta.exitosa) {
                        val token = respuesta.cuerpo.optString("token")
                        val usuarioObj = respuesta.cuerpo.optJSONObject("usuario")
                        val id = usuarioObj?.optInt("id") ?: 0
                        val nombre = usuarioObj?.optString("nombre") ?: usuario
                        Prefs.guardarSesion(this, token, id, nombre)
                        mostrarPantallaSesion()
                        registrarTokenFcmSiCorresponde()
                    } else {
                        textoErrorLogin.text = if (respuesta.codigo == 401) {
                            getString(R.string.error_credenciales)
                        } else {
                            respuesta.cuerpo.optString("error", getString(R.string.error_conexion))
                        }
                        textoErrorLogin.visibility = android.view.View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    botonIngresar.isEnabled = true
                    botonIngresar.setText(R.string.boton_ingresar)
                    textoErrorLogin.text = getString(R.string.error_conexion)
                    textoErrorLogin.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun tienePermisoUbicacion(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun tienePermisoNotificaciones(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    // No es un permiso en el sentido estricto (no hace falta pedirlo para
    // que la app funcione) — es la exclusión de la lista de optimización
    // de batería del celular. Sin ella, el fabricante puede terminar
    // matando el seguimiento en segundo plano igual, aunque el servicio
    // esté en primer plano con su notificación — por eso se pide aparte,
    // como un paso más, pero sin bloquear el resto de la app si no se da.
    private fun tieneExencionBateria(): Boolean {
        val gestor = getSystemService(Context.POWER_SERVICE) as PowerManager
        return gestor.isIgnoringBatteryOptimizations(packageName)
    }

    private fun pedirExencionBateria() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Algunos fabricantes no implementan bien esta pantalla
            // directa del sistema — si falla, se lleva a la lista
            // general de optimización de batería para que el usuario
            // busque la app a mano ahí.
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, getString(R.string.permiso_bateria_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun actualizarEstadoPermisos() {
        val faltaUbicacion = !tienePermisoUbicacion()
        val faltaNotificaciones = !tienePermisoNotificaciones()
        val faltaExencionBateria = !tieneExencionBateria()

        botonPermisoUbicacion.visibility = if (faltaUbicacion) android.view.View.VISIBLE else android.view.View.GONE
        botonPermisoNotificaciones.visibility = if (faltaNotificaciones) android.view.View.VISIBLE else android.view.View.GONE
        botonPermisoBateria.visibility = if (faltaExencionBateria) android.view.View.VISIBLE else android.view.View.GONE

        textoEstado.text = if (faltaUbicacion || faltaNotificaciones) {
            getString(R.string.permiso_ubicacion_necesario)
        } else if (faltaExencionBateria) {
            getString(R.string.permiso_bateria_necesario)
        } else {
            getString(R.string.sesion_lista)
        }

        // El seguimiento arranca en cuanto están los permisos de verdad
        // (ubicación y notificaciones) — la exclusión de batería mejora
        // que no se corte solo, pero no es indispensable para que
        // funcione, así que no se lo bloquea si todavía no se dio.
        if (!faltaUbicacion && !faltaNotificaciones) {
            registrarTokenFcmSiCorresponde()
            iniciarSeguimiento()
        }
    }

    // Prende TrackingService (seguimiento automático en horario laboral).
    // Se llama después del login y cada vez que se confirma que ya están
    // los permisos — si ya estaba corriendo, TrackingService lo detecta
    // solo y no arranca un ciclo duplicado (ver corriendo en esa clase).
    private fun iniciarSeguimiento() {
        val servicio = Intent(this, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(servicio)
        } else {
            startService(servicio)
        }
    }

    // Corta el seguimiento automático al cerrar sesión — si no, quedaría
    // mandando ubicación de un vendedor que ya no está logueado.
    private fun detenerSeguimiento() {
        stopService(Intent(this, TrackingService::class.java))
    }

    // Le avisa al servidor cuál es el token de Firebase de este celular,
    // para que lo pueda usar cuando alguien pida la ubicación de esta
    // persona. Se llama después del login y cada vez que se confirma que
    // ya están los permisos — no hace daño llamarlo de más, el servidor
    // simplemente pisa el valor anterior.
    private fun registrarTokenFcmSiCorresponde() {
        val token = Prefs.token(this) ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { tokenFcm ->
            thread {
                try {
                    ApiClient.registrarTokenFcm(token, tokenFcm)
                } catch (e: Exception) {
                    // Sin conexión en este momento — se reintenta la
                    // próxima vez que se abra la app o cambie el token.
                }
            }
        }
    }
}
