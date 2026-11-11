// Pure Kotlin — no Android. Pantry rules (expiry, quantities, units) live here so they are unit-tested
// in seconds without an emulator.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
