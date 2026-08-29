plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.ghadirb.yadavar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ghadirb.yadavar"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Same two-store setup as Maliar-Pro (see ghadirb/Maliar-Pro/app/build.gradle.kts):
    // each flavor gets its own applicationId-scoped market placeholders. "direct" is kept
    // for local/sideload testing builds that never touch either store's billing service.
    flavorDimensions += "store"
    productFlavors {
        create("direct") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"direct\"")
        }
        create("bazaar") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"bazaar\"")
        }
        create("myket") {
            dimension = "store"
            buildConfigField("String", "STORE_CHANNEL", "\"myket\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Scheduling: WorkManager for periodic/subscription reminders, AlarmManager (platform
    // API, no dependency) for exact-time alarms - same split Maliar-Pro uses.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Location-based reminders
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // JSON for backup/restore export-import
    implementation("com.google.code.gson:gson:2.10.1")

    // Home screen widget uses RemoteViews + a lightweight glance-free ListView provider -
    // no extra dependency needed beyond androidx.core, already included above.

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
