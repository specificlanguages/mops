plugins {
    id("mops.jvm-conventions")
    alias(libs.plugins.mps.platform.cache)
}

val legacyMps = configurations.register("legacyMps") { isCanBeConsumed = false }
val currentMps = configurations.register("currentMps") { isCanBeConsumed = false }

dependencies {
    legacyMps(libs.mps.legacy.distribution)
    currentMps(libs.mps.distribution)
}

fun adapterClasspath(distribution: NamedDomainObjectProvider<Configuration>) = files(
    mpsPlatformCache.getMpsRoot(distribution).map { root ->
        fileTree(root) {
            include("lib/**/*.jar", "plugins/mps-testing/**/*.jar", "plugins/mps-junit5/**/*.jar")
            exclude("**/*-src.jar", "**/*-sources.jar")
        }
    },
)

// The boot classes are visible to Ant and the worker; launcher classes need MPS's JUnit module classloader.
sourceSets.main {
    compileClasspath = adapterClasspath(legacyMps)
}
val legacy = sourceSets.create("legacy") {
    java.srcDir("src/shared/java")
    compileClasspath = adapterClasspath(legacyMps)
}
val current = sourceSets.create("current") {
    java.srcDir("src/shared/java")
    compileClasspath = adapterClasspath(currentMps)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.compilerArgs.add("-proc:none")
}

tasks.jar {
    archiveFileName = "boot.jar"
}
val legacyJar = tasks.register<Jar>("legacyJar") {
    archiveFileName = "legacy.jar"
    from(legacy.output)
}
val currentJar = tasks.register<Jar>("currentJar") {
    archiveFileName = "current.jar"
    from(current.output)
}

configurations.consumable("adapters") {
    outgoing.artifact(tasks.jar)
    outgoing.artifact(legacyJar)
    outgoing.artifact(currentJar)
}

tasks.assemble {
    dependsOn(legacyJar, currentJar)
}
