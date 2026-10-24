plugins {
    id("mops.jvm-conventions")
    kotlin("jvm")
    id("dev.detekt")
}

kotlin.compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

detekt {
    buildUponDefaultConfig = true
    config.setFrom(isolated.rootProject.projectDirectory.file("config/detekt/detekt.yml"))
    baseline = layout.projectDirectory.file("detekt-baseline.xml")
}

dependencies {
    testImplementation(kotlin("test"))
}
