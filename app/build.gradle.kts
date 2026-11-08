plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.xrayt"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    val vCode = (project.findProperty("appVersionCode") as? String)?.toIntOrNull() ?: 1
    val vName = (project.findProperty("appVersionName") as? String) ?: "1.0"

    defaultConfig {
        applicationId = "app.xrayt"
        minSdk = 24
        targetSdk = 35
        versionCode = vCode
        versionName = vName
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    // libXray.aar = gomobile build of XTLS/libXray (Xray-core), put it into app/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
