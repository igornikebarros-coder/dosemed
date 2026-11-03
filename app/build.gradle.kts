plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
android {
    namespace = "com.igorbarros.dosemed"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.igorbarros.dosemed"
        minSdk = 26
        targetSdk = 35
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = if (runNumber != null) 100 + runNumber else 1
        versionName = if (runNumber != null) "1.0.$runNumber" else "1.0-local"
    }
    val keystorePath = System.getenv("KEYSTORE_PATH")
    if (!keystorePath.isNullOrBlank()) {
        signingConfigs {
            create("dosemed") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
        buildTypes {
            getByName("debug") { signingConfig = signingConfigs.getByName("dosemed") }
            getByName("release") { signingConfig = signingConfigs.getByName("dosemed") }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
