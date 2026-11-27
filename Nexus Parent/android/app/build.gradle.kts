import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
    namespace = "com.nexus.parent"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.nexus.parent"
        minSdk = 29
        targetSdk = 37
        val releaseVersionCode = (findProperty("nexusVersionCode") as? String)?.toIntOrNull() ?: 1
        val releaseVersionName = (findProperty("nexusVersionName") as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "0.1.0-alpha"
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load Firebase credentials dynamically from monorepo root .env (two levels above Nexus Parent/android)
        val envFile = File(rootProject.projectDir.parentFile.parentFile, ".env")
        val envMap = parseEnvFile(envFile)

        buildConfigField("String", "FIREBASE_API_KEY", "\"${envMap["apiKey"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_AUTH_DOMAIN", "\"${envMap["authDomain"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${envMap["databaseURL"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${envMap["projectId"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"${envMap["appId"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"${envMap["storageBucket"] ?: ""}\"")
    }

    val keystorePath = (findProperty("nexusKeystorePath") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("NEXUS_KEYSTORE_PATH")?.trim()?.takeIf { it.isNotEmpty() }
    val keystorePassword = (findProperty("nexusKeystorePassword") as? String)
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("NEXUS_KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
    val releaseKeyAlias = (findProperty("nexusKeyAlias") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("NEXUS_KEY_ALIAS")?.trim()?.takeIf { it.isNotEmpty() }
    val releaseKeyPassword = (findProperty("nexusKeyPassword") as? String)
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("NEXUS_KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
    val hasReleaseSigning = keystorePath != null &&
        keystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null &&
        File(keystorePath).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Firebase for anonymous auth & RTDB WebRTC signaling
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.kotlinx.coroutines.play.services)

    // WebRTC P2P engine
    implementation(libs.webrtc)

    // Interactive map tiles (Esri Satellite & CartoDB Dark Streets)
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
