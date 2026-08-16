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
        versionCode = 8
        versionName = "1.7.0"
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

// Normalize the older compact UI source before Kotlin compilation. The previous
// compatibility extension made nested trailing lambdas ambiguous. These exact
// rewrites keep the existing UI behavior while producing unambiguous Kotlin.
tasks.register("sanitizeWethaqKotlin") {
    doLast {
        val src = file("app/src/main/java/com/wethaq/app/MainActivity.kt")
        var s = src.readText()
        s = s.replace("button(\"⌕  بحث عن شخص\"){search(q.text.toString())}", "button(\"⌕  بحث عن شخص\",{search(q.text.toString())})")
        s = s.replace("button(\"↻ البحث عبر الإنترنت\"){serverSearch(query)}", "button(\"↻ البحث عبر الإنترنت\",{serverSearch(query)})")
        s = s.replace("button(\"＋ إضافة إلى جهات الاتصال\"){addContact(id)}", "button(\"＋ إضافة إلى جهات الاتصال\",{addContact(id)})")
        s = s.replace("button(\"محادثة\"){chat(u)}", "button(\"محادثة\",{chat(u)})")
        s = s.replace("button(\"⧉  نسخ المعرف\"){val cm=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText(\"Wethaq ID\",myId));toast(\"تم نسخ المعرف\")}", "button(\"⧉  نسخ المعرف\",{val cm=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText(\"Wethaq ID\",myId));toast(\"تم نسخ المعرف\")})")
        s = s.replace("return@button", "return@validateIdentity")
        s = s.replace("button(action,{", "button(action,validateIdentity@{")
        src.writeText(s)
        // The overload is no longer necessary after the source is normalized.
        file("app/src/main/java/com/wethaq/app/BuildCompat.kt").writeText("package com.wethaq.app\n")
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn("sanitizeWethaqKotlin")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
