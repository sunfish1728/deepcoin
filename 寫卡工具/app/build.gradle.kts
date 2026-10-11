import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val hostingProperties = Properties().apply {
    val config = rootProject.file("../hosting.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
val hostingToken = hostingProperties.getProperty("token", "")

android {
    namespace = "com.tokencoin.writer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tokencoin.writer"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.0"
        buildConfigField("String", "HOSTING_TOKEN", "\"" + hostingToken.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
