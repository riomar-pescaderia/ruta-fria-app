plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.riomarpescaderia.rutafriaapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.riomarpescaderia.rutafriaapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Firma fija para las compilaciones debug: sin esto, cada máquina de
    // GitHub Actions genera una llave de firma distinta en cada build, y
    // Android rechaza instalar una app nueva sobre una firmada con otra
    // llave ("conflicto con un paquete"). Usando siempre este mismo
    // archivo, un vendedor puede instalar la última versión sobre la
    // anterior sin desinstalar primero.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.12.0")

    // Ubicación: un solo pedido de posición actual con buena precisión,
    // no actualizaciones continuas.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Notificaciones push (Firebase Cloud Messaging) — es lo que permite
    // que el servidor le pida la ubicación al celular en cualquier
    // momento, sin que la app esté abierta.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
