import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs.compose)
}

compose.desktop {
    application {
        mainClass = "com.yks2027.tracker.desktop.MainKt"

        buildTypes.release.proguard {
            // The Anthropic SDK (Jackson) and Room rely on reflection; shrinking is not worth the risk.
            isEnabled.set(false)
        }

        nativeDistributions {
            // jpackage only targets the host OS: .dmg is built on macOS, .msi on Windows (CI matrix).
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "YKS Takip"
            packageVersion = "2.0.0"
            description = "YKS deneme/plan/sayaç takibi — çevrimdışı, hesapsız."
            vendor = "Batuhan Ayci"
            licenseFile.set(rootProject.file("LICENSE"))
            // Room's bundled SQLite + Jackson need these JDK modules in the trimmed runtime image.
            modules("java.sql", "java.naming", "jdk.unsupported", "java.net.http")

            macOS {
                bundleID = "com.yks2027.tracker.desktop"
                dockName = "YKS Takip"
                iconFile.set(project.file("icons/yks.icns"))
            }
            windows {
                menuGroup = "YKS Takip"
                shortcut = true
                // Stable per-product GUID so .msi upgrades replace the previous install.
                upgradeUuid = "3c1f4c0e-7d2a-4f1e-9b7a-2e6c0a9d5f11"
                iconFile.set(project.file("icons/yks.ico"))
            }
        }
    }
}
