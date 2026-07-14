import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // Shared logic + shared Compose UI/presentation. kotlin.uuid.Uuid is used for IdempotencyKey.
    // i18n is done in Kotlin (see Localization.kt) rather than Compose resources, which the AGP 9
    // KMP-library plugin does not package into the Android app.
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    // --- iOS targets ----------------------------------------------------------
    // The Compose UI is shared across Android and iOS. Apple targets can only be
    // COMPILED/LINKED on macOS with Xcode — on other hosts these tasks are configured
    // but not run.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    // --------------------------------------------------------------------------

    androidLibrary {
        namespace = "com.apptolast.checkoutkmp.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // Logic
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)

            // Compose UI (shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // DI for Compose (multiplatform)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
