plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.clevs.tilim"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clevs.tilim"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.6.1"
    }

    // Chave de release vem de variáveis de ambiente (CI ou local); sem elas, cai na chave de debug.
    val keystore = System.getenv("TILIM_KEYSTORE")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("TILIM_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("TILIM_KEY_ALIAS")
            keyPassword = System.getenv("TILIM_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
