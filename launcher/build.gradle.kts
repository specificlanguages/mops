plugins {
    id("mops.jvm-conventions")
    `java-library`
}

dependencies {
    api(libs.jspecify)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
