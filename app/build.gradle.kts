plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.topdrive.garagebackup"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.topdrive.garagebackup"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    // Signature de debug FIXE (committée dans le repo, app/debug.keystore) :
    // sans ça, chaque build GitHub Actions généré sur un runner neuf recrée
    // un keystore de debug aléatoire différent, et Android refuse d'installer
    // une nouvelle APK par-dessus l'ancienne ("App not installed" / mise à
    // jour impossible) car les signatures ne correspondent plus.
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Shizuku : permet de lire le dossier Android/data d'une autre application
    // (Top Drive) sans root, en s'appuyant sur les privilèges du compte "shell"
    // (ceux qu'utilise adb), que le bac à sable de stockage d'Android 11+
    // bloque sinon pour toute app tierce même avec "Autoriser tous les fichiers".
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
