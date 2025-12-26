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

    buildTypes {
        release {
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