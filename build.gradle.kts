// Top-level build file. Version pins live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9+ has built-in Kotlin — org.jetbrains.kotlin.android must NOT be applied.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
