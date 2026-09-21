// Declara los plugins que puede usar cada módulo, sin aplicarlos acá
// (se aplican en app/build.gradle.kts) — es el patrón estándar de un
// proyecto Android con Gradle moderno.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
