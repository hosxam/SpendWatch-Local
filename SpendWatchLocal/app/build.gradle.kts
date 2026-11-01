plugins {
    id("com.android.application")
}

// Release credentials are read only from the CI environment. NEVER commit a
// signing keystore/password to this repository.
val permanentKeyPath = System.getenv("SPENDWATCH_KEYSTORE_PATH")
val permanentKeyPassword = System.getenv("SPENDWATCH_KEYSTORE_PASSWORD")

android {
    namespace = "com.hossam.spendwatch"

    signingConfigs {
        create("permanentRelease") {
            if (!permanentKeyPath.isNullOrBlank() && !permanentKeyPassword.isNullOrBlank()) {
                storeFile = file(permanentKeyPath)
                storePassword = permanentKeyPassword
                keyAlias = "spendwatch"
                keyPassword = permanentKeyPassword
            }
        }
    }
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hossam.spendwatch"
        minSdk = 24
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 8
        versionName = "4.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!permanentKeyPath.isNullOrBlank() && !permanentKeyPassword.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("permanentRelease")
            }
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
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
}
