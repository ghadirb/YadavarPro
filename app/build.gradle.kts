import java.io.File
import java.io.FileInputStream
import java.util.Properties

fun projectSetting(name: String): String =
    providers.gradleProperty(name).orNull ?: System.getenv(name).orEmpty()

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

// Local: keystore.properties (gitignored). CI: GitHub Actions secrets → env vars.
// Never hardcode passwords here — a leaked fallback would let anyone sign as us.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
} else {
    System.getenv("KEYSTORE_STORE_PASSWORD")?.let { keystoreProperties.setProperty("storePassword", it) }
    System.getenv("KEYSTORE_KEY_PASSWORD")?.let { keystoreProperties.setProperty("keyPassword", it) }
    System.getenv("KEYSTORE_KEY_ALIAS")?.let { keystoreProperties.setProperty("keyAlias", it) }
    System.getenv("KEYSTORE_STORE_FILE")?.let { keystoreProperties.setProperty("storeFile", it) }
}

val canSignRelease = !keystoreProperties.getProperty("storeFile").isNullOrBlank() &&
    !keystoreProperties.getProperty("storePassword").isNullOrBlank() &&
    !keystoreProperties.getProperty("keyAlias").isNullOrBlank()

android {
    namespace = "com.ghadirb.yadavar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ghadirb.yadavar"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Same two-store setup as Maliar-Pro. "direct" is for local/sideload testing.
    flavorDimensions += "store"
    productFlavors {
        create("direct") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"direct\"")
            buildConfigField("String", "IAB_PUBLIC_KEY", "\"\"")
        }
        create("bazaar") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"bazaar\"")
            buildConfigField("String", "IAB_PUBLIC_KEY", buildConfigString(projectSetting("BAZAAR_IAB_PUBLIC_KEY")))
        }
        create("myket") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"myket\"")
            buildConfigField("String", "IAB_PUBLIC_KEY", buildConfigString(projectSetting("MYKET_IAB_PUBLIC_KEY")))
        }
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                val storeFilePath = keystoreProperties.getProperty("storeFile")
                val f = File(storeFilePath)
                storeFile = if (f.isAbsolute) f else rootProject.file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
