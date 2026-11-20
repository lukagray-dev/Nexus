import java.io.File

plugins {
    alias(libs.plugins.android.application)
}

fun parseEnvFile(envFile: File): Map<String, String> {
    if (!envFile.exists()) return emptyMap()
    val content = envFile.readText()
    val map = mutableMapOf<String, String>()
    val keys = listOf(
        "apiKey", "authDomain", "databaseURL", "projectId", "storageBucket",
        "messagingSenderId", "appId", "measurementId"
    )
    for (k in keys) {
        val regex = Regex("""['"]?$k['"]?\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        val match = regex.find(content)
        if (match != null) {
            map[k] = match.groupValues[1]
        }
    }
    return map
}

android {
    namespace = "com.nexus.child"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.nexus.child"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load Firebase credentials dynamically from root .env file
        val envFile = File(rootProject.projectDir.parentFile, ".env")
        val envMap = parseEnvFile(envFile)

        buildConfigField("String", "FIREBASE_API_KEY", "\"${envMap["apiKey"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${envMap["databaseURL"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${envMap["projectId"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"${envMap["appId"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"${envMap["storageBucket"] ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Firebase for signaling & anonymous auth
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Play Services Location
    implementation(libs.play.services.location)

    // WebRTC
    implementation(libs.webrtc)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
