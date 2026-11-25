plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "com.karima.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.karima.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0-trinity"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
