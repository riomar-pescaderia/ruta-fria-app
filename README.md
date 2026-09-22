# Ruta Fría — app de vendedores

App para Android que le permite a un vendedor mandar su ubicación actual
cuando se la piden desde Ruta Fría (sección "Ubicación", solo para
administradores) — no hay rastreo continuo: mientras nadie la pide, la
app no manda nada.

## Cómo funciona, en resumen

1. El vendedor instala la app en su celular y entra con el mismo usuario
   y contraseña que ya usa en Ruta Fría desde la computadora.
2. La app pide dos permisos, una sola vez: ubicación y notificaciones.
3. Desde Ruta Fría, un administrador toca "Localizar ahora" al lado del
   nombre del vendedor.
4. Eso le manda una notificación al celular (por Firebase, gratis) — la
   app la recibe, toma la posición del GPS en ese momento, y se la manda
   de vuelta al sistema. Todo esto pasa aunque la app esté minimizada,
   sin que el vendedor tenga que tocar nada.
5. El punto aparece en el mapa de Ubicación de Ruta Fría.

## Antes de compilarla: crear el proyecto de Firebase (gratis, 10 minutos)

Esto hace falta una sola vez:

1. Entrá a [Firebase Console](https://console.firebase.google.com/) con
   una cuenta de Google (idealmente una de la empresa, no la personal de
   nadie) y creá un proyecto nuevo — el nombre no importa, por ejemplo
   "Ruta Fría".
2. Dentro del proyecto: **Configuración del proyecto** (el engranaje) →
   pestaña **General** → "Tus apps" → ícono de Android → agregá una app
   con el nombre de paquete exacto `com.riomarpescaderia.rutafriaapp`.
3. Te va a ofrecer descargar un archivo `google-services.json` —
   descargalo y reemplazá el que ya está en `app/google-services.json`
   de este proyecto (es un archivo de relleno, hay que cambiarlo por el
   real).
4. Todavía dentro de Configuración del proyecto → pestaña **Cuentas de
   servicio** → botón "Generar nueva clave privada" → se descarga otro
   archivo JSON (distinto al anterior). Ese es el que necesita el
   **servidor** (no la app) para poder mandar las notificaciones.
5. En Render → el servicio "ruta-fria" (la web, no la base de datos) →
   Environment → agregar una variable nueva:
   - Nombre: `FIREBASE_SERVICE_ACCOUNT`
   - Valor: todo el contenido de ese segundo archivo JSON, pegado tal
     cual, en una sola variable (Render permite pegar texto largo/con
     saltos de línea sin problema).
6. Guardar — Render redeploya solo y ya queda conectado.

Sin este paso, la app compila y funciona igual (login, permisos), pero el
botón "Localizar ahora" en Ruta Fría va a avisar que falta terminar de
conectar Firebase.

## Cómo se compila (no hace falta instalar Android Studio)

Este proyecto ya viene con un workflow de GitHub Actions
(`.github/workflows/build-apk.yml`) que compila el APK solo, en cada
cambio que subas a la rama `main` — el mismo estilo que ya conocés con
Render, pero para la app.

1. Creá un repositorio nuevo en GitHub (por ejemplo
   `riomar-pescaderia/ruta-fria-app`) y subí esta carpeta completa.
2. Entrá a la pestaña **Actions** del repositorio — ahí se ve el
   progreso de la compilación (tarda 2-3 minutos la primera vez).
3. Cuando termina, andá a la página principal del repositorio → sección
   **Releases** (a la derecha) → "Última versión de la app" → descargá
   `app-debug.apk`. Ese link se actualiza solo cada vez que subís un
   cambio.

## Instalar el APK en cada celular

Android no deja instalar apps fuera de Google Play por defecto — hay que
habilitarlo una vez por celular:

1. Descargá `app-debug.apk` en el celular (desde el link de Releases,
   por WhatsApp, o como te resulte más cómodo).
2. Al abrirlo, Android va a pedir permiso para "instalar apps
   desconocidas" desde esa fuente (el navegador o la app de archivos) —
   aceptar.
3. Instalar. Abrir la app, entrar con el usuario del vendedor, y aceptar
   los dos permisos que pide (ubicación y notificaciones).

## Para que funcione de forma confiable

- El vendedor no tiene que dejar la app abierta — alcanza con que esté
  instalada, con la sesión iniciada y los permisos dados. Puede usar el
  celular con normalidad, y **no pasa nada si la cierra deslizándola
  fuera de la lista de apps recientes**: eso no la apaga a nivel del
  sistema. La notificación de "localizar ahora" la despierta igual,
  exactamente como le llegan los mensajes a WhatsApp aunque la hayas
  cerrado así.
- Lo único que sí corta esto de verdad es ir a Ajustes del celular →
  Apps → Ruta Fría → **"Forzar detención"** — ese botón (distinto de
  cerrarla normalmente) le dice a Android que no la despierte para nada
  hasta que se abra de nuevo a mano.
- En algunas marcas de celular con "ahorro de batería" agresivo (Xiaomi,
  Huawei y similares — Samsung y los Android "puros" en general no dan
  problema) el sistema puede tratar el "deslizar para cerrar" como si
  fuera forzar la detención, salvo que el vendedor la haya marcado como
  app permitida en segundo plano. Para esos casos puede hacer falta,
  una vez, desactivar la optimización de batería para esta app
  específica desde Ajustes del celular, para que las notificaciones
  lleguen siempre.

## Estructura del proyecto

```
app/src/main/java/com/riomarpescaderia/rutafriaapp/
  MainActivity.kt      pantalla de login y permisos
  ApiClient.kt          llamadas a la API de Ruta Fría
  Prefs.kt               guarda el token de sesión del celular
  FcmService.kt          recibe el aviso de "mandá tu ubicación"
  LocationService.kt     toma el GPS y lo manda, y se apaga solo
```
