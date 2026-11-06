import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Secrets stay in local.properties (gitignored), e.g. tmdb.apiKey=<v3 key or v4 read access token>
val localProps = Properties().apply {
    listOf("local.properties", "glasshouse.properties").forEach { name ->
        rootProject.file(name).takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
}

android {
    namespace = "com.cleantv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cleantv.launcher"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Personal preferences; see glasshouse.properties.example. Everything has a neutral default.
        fun setting(name: String, key: String, default: String = "") = buildConfigField(
            "String", name, "\"${localProps.getProperty(key, default).trim().replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        setting("HOME_NAME", "home.name")
        setting("HOME_LATITUDE", "home.latitude")
        setting("HOME_LONGITUDE", "home.longitude")
        setting("HOME_ZONE", "home.timeZone")
        setting("SECOND_CLOCK", "home.secondClock")
        setting("REGIONS", "regions", "US:United States,GB:United Kingdom,IN:India")
        setting("REMOTE_BUTTON_APP", "remote.button9.app")
        setting("REMOTE_BUTTON_SCAN", "remote.button9.scanCode", "491")
        setting("PARTY_TITLE", "party.title", "Diwali Party")
        setting("PARTY_HOSTS", "party.hosts")
        setting("PARTY_TEAMS", "party.teams", "Team Diya,Team Rangoli")
        setting("PARTY_PLAYLISTS", "party.playlists", "Bollywood party|spotify:playlist:1bzEiznTmu3KPdEQGIRVQV;All-time dance hits|spotify:playlist:4DiKHJyVrPrz3gO5yKDTc8;Latest Hindi hits|spotify:playlist:37i9dQZF1DX0XUfTFmNBRM")
        buildConfigField("String", "TMDB_KEY", "\"${localProps.getProperty("tmdb.apiKey", "")}\"")
        buildConfigField("String", "GOVEE_KEY", "\"${localProps.getProperty("govee.apiKey", "").trim()}\"")
        buildConfigField("String", "GEMINI_KEY", "\"${localProps.getProperty("gemini.apiKey", "").trim()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideloaded onto our own TV only, so the local debug key is fine; release mostly matters for
            // speed (non-debuggable builds run Compose several times faster).
            signingConfig = signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Installs Compose's precompiled hot paths even when sideloaded; without it the UI runs interpreted at first.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.tv.material)
    implementation(libs.haze)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)
}
