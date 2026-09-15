import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Firebase configuration is intentionally not committed. Local and CI builds without
// google-services.json still compile and test; configured release/debug builds apply
// the plugin and get a working FirebaseApp at runtime.
val hasGoogleServicesConfig = file("google-services.json").exists() ||
    file("src/debug/google-services.json").exists() ||
    file("src/release/google-services.json").exists()
if (hasGoogleServicesConfig) apply(plugin = "com.google.gms.google-services")

val debugApiBaseUrl = providers.gradleProperty("maincourse.apiBaseUrl")
    .orElse("http://10.0.2.2:3000/").get()
val debugApiUri = URI(debugApiBaseUrl)
require(debugApiUri.scheme in listOf("http", "https") && debugApiUri.host != null &&
    debugApiUri.userInfo == null && debugApiUri.query == null && debugApiUri.fragment == null &&
    debugApiBaseUrl.endsWith("/")) {
    "maincourse.apiBaseUrl must be an HTTP(S) base URL ending in /, without credentials, query, or fragment"
}
require(debugApiUri.scheme == "https" || debugApiUri.host in listOf("10.0.2.2", "localhost", "127.0.0.1")) {
    "Debug HTTP is limited to emulator/loopback hosts; use an HTTPS tunnel or adb reverse for a physical device"
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.exists()) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.getmaincourse.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.getmaincourse.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseSigning = if (releaseSigningPropertiesFile.exists()) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
            storePassword = releaseSigningProperties.getProperty("storePassword")
            keyAlias = releaseSigningProperties.getProperty("keyAlias")
            keyPassword = releaseSigningProperties.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            buildConfigField("String", "API_BASE_URL", "\"https://app.getmaincourse.com/\"")
            releaseSigning?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
        // Dependency upgrades are deliberate; online freshness checks make CI time-dependent.
        disable += setOf("NewerVersionAvailable", "AndroidGradlePluginVersion", "GradleDependency")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.fragment)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.room.testing)
    // Compose's transitive Espresso 3.5 uses an input API removed in Android 17.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
