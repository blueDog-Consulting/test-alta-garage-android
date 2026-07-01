import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties().apply {
    if (versionPropertiesFile.exists()) {
        load(FileInputStream(versionPropertiesFile))
    }
}

android {
    namespace = "dev.bluedog.garagedoor"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.bluedog.garagedoor"
        minSdk = 23
        targetSdk = 35
        versionCode = (versionProperties["versionCode"] as String?)?.toInt() ?: 1
        versionName = versionProperties["versionName"] as String? ?: "1.0.0"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = signingPassword("storePassword")
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = signingPassword("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

fun signingPassword(propertyKey: String): String {
    val envKey = when (propertyKey) {
        "storePassword" -> "KEYSTORE_PASSWORD"
        "keyPassword" -> "KEY_PASSWORD"
        else -> error("Unknown signing password key: $propertyKey")
    }
    return System.getenv(envKey)
        ?: (keystoreProperties[propertyKey] as String?)
        ?: error("Missing $propertyKey in keystore.properties or $envKey env var")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.car.app:app:1.4.0")
}
