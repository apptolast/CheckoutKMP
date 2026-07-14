import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    // The shared module now owns the Compose UI + presentation; :androidApp is a thin host.
    implementation(projects.shared)

    // Compose host essentials (the UI itself lives in :shared)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)

    // Koin: start the graph with the Android context (koinViewModel lives in :shared via koin-compose)
    implementation(libs.koin.android)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.uiTooling)

    // Unit tests (JVM)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

android {
    namespace = "com.apptolast.checkoutkmp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.apptolast.checkoutkmp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}