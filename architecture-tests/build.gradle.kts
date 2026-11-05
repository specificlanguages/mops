plugins {
    id("mops.kotlin-jvm-conventions")
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    // Konsist reads the other modules' sources from disk, so Gradle must see them as inputs to rerun the tests when
    // they change.
    inputs.files(fileTree(layout.settingsDirectory) { include("*/src/**") })
        .withPropertyName("moduleSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
