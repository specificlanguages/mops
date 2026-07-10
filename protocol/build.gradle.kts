plugins {
    id("mops.kotlin-jvm-conventions")
    `java-library`
    kotlin("plugin.serialization") version "2.3.21"
    id("com.gradleup.shadow") version "9.0.0"
}

// kotlinx.serialization is shaded and relocated into the protocol jar so it can never shadow MPS's own copy on the
// daemon's flat classpath. It is compiled against the real package but shipped only under the relocated package.
val relocatedSerialization: Configuration by configurations.creating

configurations {
    compileOnly { extendsFrom(relocatedSerialization) }
    testImplementation { extendsFrom(relocatedSerialization) }
}

dependencies {
    relocatedSerialization("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

val relocatedPackage = "com.specificlanguages.mops.shaded.kotlinx.serialization"

tasks.shadowJar {
    // Keep the default "all" classifier so the shaded jar never collides with the thin jar used for compilation.
    configurations = listOf(relocatedSerialization)
    relocate("kotlinx.serialization", relocatedPackage)

    // The Kotlin standard library and its annotations are provided by MPS and by the consuming distributions; bundling
    // them would shadow those copies on the daemon's flat classpath.
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*:.*"))
        exclude(dependency("org.jetbrains:annotations:.*"))
    }
}

// Compile against the plain protocol classes, but run against the shaded jar with the relocated runtime bundled in.
configurations.runtimeElements {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.shadowJar)
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
