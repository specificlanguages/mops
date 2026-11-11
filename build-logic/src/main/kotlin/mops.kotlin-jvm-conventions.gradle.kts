plugins {
    id("mops.jvm-conventions")
    kotlin("jvm")
    id("dev.detekt")
}

kotlin.compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17

detekt {
    buildUponDefaultConfig = true
    config.setFrom(layout.settingsDirectory.file("config/detekt/detekt.yml"))
    // detektMain reads and detektBaselineMain writes detekt-baseline-main.xml.
    baseline = layout.projectDirectory.file("detekt-baseline.xml")
}

// Run detekt with type resolution on the main source set instead of the plain `detekt` task the plugin attaches to
// `check`.
tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "detekt" })
    dependsOn("detektMain")
}

dependencies {
    testImplementation(kotlin("test"))
}

dependencies {
    detektPlugins(project(":detekt-rules"))
}
