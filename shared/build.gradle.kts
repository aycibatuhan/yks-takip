import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    androidLibrary {
        namespace = "com.yks2027.tracker.shared"
        compileSdk = 37
        minSdk = 29
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        // jvmMain: code shared by Android + desktop that may use JDK APIs (java.time, Locale,
        // the Anthropic Java SDK). commonMain holds platform-neutral contracts only. The web
        // phase moves jvmMain pieces down to commonMain (kotlinx-datetime, Ktor) — nothing
        // here blocks that: persistence and networking sit behind interfaces.
        val jvmMain by creating { dependsOn(commonMain.get()) }
        // NOTE: a test source set must never dependsOn a main source set — that would pull
        // main sources in as test fragments (and lose the platform actuals). Main visibility
        // comes from the associated compilation.
        val jvmTest by creating { dependsOn(commonTest.get()) }
        androidMain.get().dependsOn(jvmMain)
        val desktopMain by getting { dependsOn(jvmMain) }
        val desktopTest by getting { dependsOn(jvmTest) }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.jb.material3)
            implementation(libs.jb.material3.adaptive)
            implementation(libs.jb.material3.adaptive.navigation.suite)
            implementation(libs.jb.material.icons.extended)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.jb.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.room.runtime)
            implementation(libs.datastore.preferences.core)
            implementation(libs.okio)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.markdown.renderer.m3)
        }
        jvmMain.dependencies {
            // JVM-only for this phase (Android + desktop are both JVM); see ARCHITECTURE §Platform boundary.
            implementation(libs.anthropic.java)
            implementation(libs.okhttp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.documentfile)
            // Charts: Vico stays Android-only (its multiplatform line is a different, older API).
            implementation(libs.vico.compose.m3)
            implementation(libs.koin.android)
        }
        desktopTest.dependencies {
            // Offscreen UI rendering for the desktop tour + keyboard-flow tests (no screen permission needed).
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.common)
            // Desktop opens yks.db with BundledSQLiteDriver; Android keeps the framework SQLite.
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.swing)
            // Native file dialogs (spec: FileKit — verified maintained, 0.15.0 on Maven Central).
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs.compose)
        }
    }
}

ksp {
    // Schemas are committed (shared/schemas) — every schema change stays a testable migration.
    // (The Room Gradle plugin exported nothing for these KMP targets — copyRoomSchemas NO-SOURCE —
    // so the classic processor option is used; both targets write the identical file.)
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}
