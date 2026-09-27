plugins {
    id("mops.kotlin-jvm-conventions")
    `java-library`
}

dependencies {
    api(project(":protocol"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
