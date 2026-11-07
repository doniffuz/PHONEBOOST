import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Google'ning rasmiy TEST reklama ID lari — FAQAT debug build uchun.
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/6300978111"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"

fun prop(name: String, fallback: String = ""): String =
    (findProperty(name) as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

// Release'da reklama FAQAT to'rtala haqiqiy ID berilganda yoqiladi. Aks holda reklama kodda o'chiriladi
// (test ID lar production'da hech qachon reklama ko'rsatmaydi). TODO: haqiqiy AdMob ID larni README dagi kalitlar bilan bering.
val adsReleaseReady = listOf("PB_ADMOB_APP_ID", "PB_AD_BANNER_ID", "PB_AD_INTERSTITIAL_ID", "PB_AD_REWARDED_ID")
    .all { prop(it).isNotEmpty() }
if (!adsReleaseReady) {
    logger.warn("PHONEBOOST: release uchun haqiqiy AdMob ID lari berilmagan — release'da reklama O'CHIRILGAN. README ga qarang.")
}

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
        versionCode = 3
        versionName = "0.3.0"

        // Loyiha egasi gradle.properties orqali beradi (bo'sh = tugma o'chiq). Kodga yozilmaydi.
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
            // Development vaqtida doim test ID.
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("String", "AD_BANNER_ID", "\"$testBannerId\"")
            buildConfigField("String", "AD_INTERSTITIAL_ID", "\"$testInterstitialId\"")
            buildConfigField("String", "AD_REWARDED_ID", "\"$testRewardedId\"")
        }
        release {
            // Test ID release'da ishlatilmaydi. Manifestdagi APPLICATION_ID qiymati SDK crash qilmasligi uchun majburiy;
            // reklamalar o'chiq bo'lsa u hech narsa ko'rsatmaydi (ADS_ENABLED=false, kodda init qilinmaydi).
            manifestPlaceholders["admobAppId"] = if (adsReleaseReady) prop("PB_ADMOB_APP_ID") else testAppId
            buildConfigField("boolean", "ADS_ENABLED", adsReleaseReady.toString())
            buildConfigField("String", "AD_BANNER_ID", "\"${prop("PB_AD_BANNER_ID")}\"")
            buildConfigField("String", "AD_INTERSTITIAL_ID", "\"${prop("PB_AD_INTERSTITIAL_ID")}\"")
            buildConfigField("String", "AD_REWARDED_ID", "\"${prop("PB_AD_REWARDED_ID")}\"")
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

    // Reklama (AdMob). Ilovaning asosiy funksiyalari reklamaga bog'liq emas.
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
