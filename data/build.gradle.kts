// Stores the pantry (Room 3) and the Pantry settings screen's values (DataStore), and maps both to :domain.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "io.cosgriff.pantry.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // MigrationTest reads every exported schema and runs on the JVM (Robolectric), so it is part of `build`.
    sourceSets {
        named("test") {
            assets.directories.add("$projectDir/schemas")
        }
    }
    testOptions.unitTests.isIncludeAndroidResources = true

    // As in :app: no warnings are kept, and version news is left to Dependabot.
    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += "NewerVersionAvailable"
        disable += "AndroidGradlePluginVersion"
    }
}

kotlin {
    jvmToolchain(17)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":domain"))

    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.datastore.preferences)

    // api: Hilt generates the app's component in :app, so it must see these binding types.
    api(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.androidx.sqlite.framework)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
