plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.superjet.notificationmonitor"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.superjet.notificationmonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "3.6.4-SUPPORT-PROOF-FINAL"
        buildConfigField("String", "SUPERJET_BASE_URL", "\"https://superjet.tail0f920c.ts.net:8443\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
}
