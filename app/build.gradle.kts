plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wethaq.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wethaq.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.3.0"
    }

    val keystorePath = System.getenv("WETHAQ_KEYSTORE")
    val hasReleaseSigning = !keystorePath.isNullOrBlank()

    if (hasReleaseSigning) {
        signingConfigs {
            create("wethaqRelease") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("WETHAQ_STORE_PASSWORD")
                keyAlias = System.getenv("WETHAQ_KEY_ALIAS")
                keyPassword = System.getenv("WETHAQ_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug { isDebuggable = true }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("wethaqRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
