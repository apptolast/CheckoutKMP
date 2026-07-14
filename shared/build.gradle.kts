import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    // Shared logic is pure Kotlin: domain + data live here, no Android and no UI.
    // kotlin.uuid.Uuid is used for IdempotencyKey.
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    // --- iOS targets ----------------------------------------------------------
    // commonMain stays platform-agnostic (coroutines, datetime, koin-core, stdlib),
    // so no iosMain actuals are required. Apple targets can only be COMPILED/LINKED
    // on macOS with Xcode — on other hosts these tasks are configured but not run.
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
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
