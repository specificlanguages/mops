plugins {
    id("mops.jvm-conventions")
    kotlin("jvm")
}

kotlin.compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

dependencies {
    testImplementation(kotlin("test"))
}
