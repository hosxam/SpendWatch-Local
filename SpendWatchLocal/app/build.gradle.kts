plugins {
    id("com.android.application")
}

android {
    namespace = "com.hossam.spendwatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hossam.spendwatch"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "4.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Only standard AndroidX dependencies - No internet or networking libraries!
}
