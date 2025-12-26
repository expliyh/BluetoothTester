plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "top.expli.bluetoothtester"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "top.expli.bluetoothtester"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val keyAliasName = System.getenv("ANDROID_KEY_ALIAS")
    val keyAliasPassword = System.getenv("ANDROID_KEY_ALIAS_PASSWORD")
    val hasSigning = listOf(
        keystorePath,
        keystorePassword,
        keyAliasName,
        keyAliasPassword
    ).run { all { !it.isNullOrBlank() } }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyAliasPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig =
                if (hasSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}