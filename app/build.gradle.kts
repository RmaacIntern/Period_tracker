plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.aivigil.periodtracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aivigil.periodtracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        viewBinding = true
        // AGP 8+ no longer generates BuildConfig unless asked. Needed so
        // AdsRemoteConfig can use a 1-second Remote Config fetch interval in
        // debug builds and the correct 1-hour interval in release.
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    // Room schema export — required by MigrationTestHelper so migrations can be
    // validated in an instrumented test instead of being discovered in production.
    // Commit the generated schemas/ directory.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Existing
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.animation.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ViewModel + LiveData + Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Firebase BoM & Analytics
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    // Firebase Remote Config — controls ads on/off remotely
    implementation("com.google.firebase:firebase-config-ktx")

    // Google Mobile Ads SDK (AdMob)
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0")

    implementation("io.github.usefulness:shimmer-android-core:1.0.0")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ✅ ADD inside dependencies { }
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}