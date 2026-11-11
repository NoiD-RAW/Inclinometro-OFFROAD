plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.arklay.inclinometro"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.arklay.inclinometro"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "2.1"
    }
    // Firma de las versiones publicadas: la clave llega por variables de entorno (secretos de GitHub Actions), nunca en el repositorio.
    val keystore = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") { if (keystore != null) signingConfig = signingConfigs.getByName("release") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.20")      // mapas OpenStreetMap, sin clave de API
}
