plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber: Int = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val hasKey: Boolean = keystorePath != null && File(keystorePath).exists()

android {
    namespace = "com.livetv.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.livetv.app"
        minSdk = 21
        targetSdk = 34
        versionCode = buildNumber
        versionName = "6.$buildNumber"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (hasKey) {
                storeFile = File(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-transformer:1.3.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
