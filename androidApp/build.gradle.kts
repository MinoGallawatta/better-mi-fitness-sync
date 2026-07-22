import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Tag-driven releases: -PversionName=1.2.3 -PversionCode=1002003
// or env VERSION_NAME / VERSION_CODE. Local defaults stay 1.0.0 / 1.
fun appVersionProp(name: String, envKey: String, default: String): String =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: default

val appVersionName: String = appVersionProp("versionName", "VERSION_NAME", "1.0.0")
val appVersionCode: Int = appVersionProp("versionCode", "VERSION_CODE", "1").toInt()

// Strava API app credentials for this personal build — never distributed, so it's safe to
// ship a client_secret (see StravaConfig). Populate strava.clientId / strava.clientSecret
// in the gitignored local.properties at the repo root; both default to "" if absent.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun stravaProp(key: String): String = localProperties.getProperty(key, "")

android {
    namespace = "com.bettermifitness.sync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bettermifitness.sync"
        minSdk = 28
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "STRAVA_CLIENT_ID", "\"${stravaProp("strava.clientId")}\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"${stravaProp("strava.clientSecret")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9 built-in Kotlin: configure JVM target without kotlin-android plugin.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.health.connect)
}
