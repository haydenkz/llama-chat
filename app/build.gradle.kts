plugins {
    alias(libs.plugins.android.application)
    // AGP 9 provides built-in Kotlin support, so the kotlin-android plugin
    // must NOT be applied. These are Kotlin *compiler* plugins only.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.llamacpp.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.llamacpp.mobile"
        minSdk = 29
        targetSdk = 37
        versionCode = 7
        versionName = "0.1.6"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the standard debug key so release APKs are installable
            // (sideload) without extra secrets. Replace with a real signing config
            // before publishing to a store.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// With built-in Kotlin, kotlin.compilerOptions.jvmTarget defaults to
// android.compileOptions.targetCompatibility (17), so it is not set here.

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.code)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.exp4j)

    testImplementation(libs.junit)
}
