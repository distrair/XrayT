plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = java.util.Properties().apply {
    val propertiesFile = rootProject.file("signing.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}
fun signingValue(propertyName: String, environmentName: String): String? =
    providers.environmentVariable(environmentName).orNull
        ?: signingProperties.getProperty(propertyName)

val releaseStoreFile = signingValue("storeFile", "ANDROID_KEYSTORE_PATH")
val releaseStorePassword = signingValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() } && releaseStoreFile?.let { rootProject.file(it).isFile } == true

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

    if (hasReleaseSigning) {
        signingConfigs.create("release") {
            storeFile = file(releaseStoreFile!!)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        doFirst {
            check(hasReleaseSigning) {
                "Release signing is not configured. Set ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD, or provide them in signing.properties."
            }
        }
    }
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
