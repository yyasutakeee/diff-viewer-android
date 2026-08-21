plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
val hasCiSigningConfiguration = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.example.diffviewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.diffviewer"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    val ciSigningConfig = if (hasCiSigningConfiguration) {
        signingConfigs.create("ci") {
            storeFile = file(requireNotNull(signingStoreFile))
            storePassword = requireNotNull(signingStorePassword)
            keyAlias = requireNotNull(signingKeyAlias)
            keyPassword = requireNotNull(signingKeyPassword)
            storeType = "PKCS12"
        }
    } else {
        null
    }

    buildTypes {
        debug {
            ciSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
