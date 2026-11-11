plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kover)
}

// CI passes -PversionName=1.2.0 -PversionCode=<run number>. Local builds use version.txt, which the
// release PR keeps current.
val appVersionName = (findProperty("versionName") as String?)
    ?: rootProject.file("version.txt").takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    ?: "0.1.0"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

android {
    namespace = "io.cosgriff.pantry"
    compileSdk = 37

    defaultConfig {
        // Never change after the first release: Android and Obtainium treat a new ID as a new app.
        applicationId = "io.cosgriff.pantry"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Set by the GitHub release workflow from the `release` environment's secrets.
            val ks = System.getenv("PANTRY_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("PANTRY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PANTRY_KEY_ALIAS")
                keyPassword = System.getenv("PANTRY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("PANTRY_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    androidResources {
        // The app's languages for Android's per-app language setting, from the values-xx folders;
        // res/resources.properties says the default folder is US English.
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // The screenshot tests draw screens under Robolectric, which needs the app's resources and fonts.
    testOptions.unitTests.isIncludeAndroidResources = true
    // Gradle's default 512 MB test heap is too little for Robolectric drawing Compose screens.
    testOptions.unitTests.all { it.maxHeapSize = "2g" }

    lint {
        abortOnError = true
        // No warnings are kept: a new one fails the build, as an error does.
        warningsAsErrors = true
        // Dependabot proposes new versions after its cooldown; lint would flag them on day one.
        disable += "NewerVersionAvailable"
        disable += "AndroidGradlePluginVersion"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.accessibility.check)
    // The empty activity the Compose test rule draws into; only in the debug manifest, never the release.
    debugImplementation(libs.compose.ui.test.manifest)
}

// Feeds the merged coverage report (root build.gradle.kts): the debug unit tests only, so the screenshot
// tests aren't run twice.
kover {
    currentProject {
        createVariant("coverage") { add("debug") }
    }
}
