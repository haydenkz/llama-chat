buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 ships built-in Kotlin (KGP 2.2.x). Bump KGP to match the
        // Kotlin/Compose/KSP versions declared in the version catalog.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
