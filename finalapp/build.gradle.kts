plugins {
    id("com.android.application")
}

android {
    namespace = "com.wethaq.finalapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wethaq.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 11
        versionName = "2.0.0"
    }

    val ks = System.getenv("WETHAQ_KEYSTORE")
    if (!ks.isNullOrBlank()) {
        signingConfigs {
            create("releaseKey") {
                storeFile = file(ks)
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
            if (!ks.isNullOrBlank()) signingConfig = signingConfigs.getByName("releaseKey")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
