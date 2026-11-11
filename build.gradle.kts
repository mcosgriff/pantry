plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.kover)
}

subprojects {
    // A failing test prints its whole stack: CI's log is often the only place a failure can be read.
    tasks.withType<Test>().configureEach {
        testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }
}

// Test coverage (Codecov, #5): `./gradlew koverXmlReportCoverage` runs the unit tests of :domain and :app
// here, with a `coverage` variant in its build file, to measure it too.
// here, and a `coverage` variant in its build file, once it exists.
dependencies {
    kover(project(":domain"))
    kover(project(":app"))
}

kover {
    currentProject {
        createVariant("coverage") {}
    }
    reports {
        filters {
            excludes {
                // Generated code: Compose's singletons, BuildConfig and resources.
                classes("*ComposableSingletons*", "*.BuildConfig", "*.R", "*.R\$*")
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
    }
}

// :domain's coverage variant is set here, so its own build file stays the plain Kotlin module it is.
project(":domain") {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        currentProject {
            createVariant("coverage") { add("jvm") }
        }
    }
}

// Robolectric's Android jars. Left to itself, Robolectric fetches android-all-instrumented from Maven
// Central at test time, outside Gradle: no cache holds it, and a Maven Central hiccup fails every
// Robolectric test in CI. Here Gradle resolves the jars (one per SDK the tests run on, versions in
// libs.versions.toml beside `robolectric`), copies them to build/robolectric, and every unit test task,
// Roborazzi's and Kover's runs included, reads them from there with Robolectric kept offline.
abstract class RobolectricOffline : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val jars: DirectoryProperty

    override fun asArguments() = listOf(
        "-Drobolectric.offline=true",
        "-Drobolectric.dependency.dir=${jars.get().asFile.absolutePath}",
    )
}

configure(listOf(project(":app"), project(":data"))) {
    // One configuration per jar: in one, Gradle would keep only the highest of the versions of the one module.
    val androidAll = mapOf(
        "Sdk35" to rootProject.libs.robolectric.androidAll.sdk35,
    ).map { (sdk, jar) ->
        configurations.create("robolectricAndroidAll$sdk") {
            isCanBeConsumed = false
            isTransitive = false
        }.also { dependencies.add(it.name, jar) }
    }
    val copyJars = tasks.register<Sync>("robolectricAndroidAll") {
        description = "Copies the Android jars Robolectric runs the unit tests on, so it never downloads them."
        androidAll.forEach { from(it) }
        into(layout.buildDirectory.dir("robolectric"))
    }
    val offline = objects.newInstance<RobolectricOffline>().apply {
        jars.fileProvider(copyJars.map { it.destinationDir })
    }
    tasks.withType<Test>().configureEach { jvmArgumentProviders.add(offline) }
}
