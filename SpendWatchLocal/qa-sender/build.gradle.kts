plugins {
    id("com.android.application")
}

android {
    namespace = "com.hossam.spendwatch.qasender"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hossam.spendwatch.qasender"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
}
