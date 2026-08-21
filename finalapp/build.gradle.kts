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
        versionCode = 20
        versionName = "2.0.0"
    }

    val ks = System.getenv("WETHAQ_KEYSTORE")
    val storePassword = System.getenv("WETHAQ_STORE_PASSWORD")
    val keyAlias = System.getenv("WETHAQ_KEY_ALIAS")
    val keyPassword = System.getenv("WETHAQ_KEY_PASSWORD")

    signingConfigs {
        create("releaseKey") {
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug { isDebuggable = true }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseKey")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
