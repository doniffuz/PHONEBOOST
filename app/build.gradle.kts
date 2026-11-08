import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun prop(name: String, fallback: String = ""): String =
    (findProperty(name) as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "uz.phoneboost.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.phoneboost.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"

        // Loyiha egasi gradle.properties orqali beradi (bo'sh = tugma o'chiq). Kodga yozilmaydi.
        buildConfigField("String", "SUPPORT_URL", "\"${prop("PB_SUPPORT_URL")}\"")
        buildConfigField("String", "DONATION_CONFIG_URL", "\"${prop("PB_DONATION_CONFIG_URL")}\"")
        buildConfigField("String", "UPDATE_URL", "\"${prop("PB_UPDATE_URL")}\"")
        buildConfigField("String", "ADMIN_API_URL", "\"${prop("PB_ADMIN_API_URL")}\"")
        buildConfigField("String", "DONATION_URL", "\"${prop("PB_DONATION_URL")}\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"${prop("PB_CONTACT_EMAIL")}\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${prop("PB_PRIVACY_POLICY_URL")}\"")
    }

    signingConfigs {
        // Kalit/parol kodda yo'q: keystore.properties (gitignore'da) dan o'qiladi. Fayl bo'lmasa release imzosiz chiqadi.
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Fon diagnostikasi (Avtomatlashtirish)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")

}
