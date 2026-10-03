plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace="com.bharatpdf.app"
    compileSdk=35
    defaultConfig {
        applicationId="com.bharatpdf.app"
        minSdk=23
        targetSdk=35
        versionCode=1
        versionName="1.0"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
