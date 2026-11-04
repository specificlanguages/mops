plugins {
    id("mops.kotlin-jvm-conventions")
    `java-library`
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

// kotlinx.serialization is shaded and relocated into the protocol jar so it can never shadow MPS's own copy on the
// daemon's flat classpath. It is compiled against the real package but shipped only under the relocated package.
val relocatedSerialization = configurations.register("relocatedSerialization") { isCanBeConsumed = false }

configurations {
    compileOnly { extendsFrom(relocatedSerialization) }
    testImplementation { extendsFrom(relocatedSerialization) }
}

dependencies {
    relocatedSerialization(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val relocatedPackage = "com.specificlanguages.mops.shaded.kotlinx.serialization"

val generateMopsVersion = tasks.register<WriteProperties>("generateMopsVersion") {
    destinationFile = layout.buildDirectory.file("generated/mops-version/mops-version.properties").get().asFile
    property("version", project.version.toString())
    // Read by git at execution time, so a commit or an edit does not invalidate the configuration cache. An empty
    // revision means the source tree has no git metadata or git could not run.
    property(
        "revision",
        providers.of(GitRevision::class) { parameters.rootDirectory = isolated.rootProject.projectDirectory }
            .orElse(""),
    )
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/mops-version"))
}

tasks.processResources {
    dependsOn(generateMopsVersion)
}

tasks.test {
    systemProperty("mops.expectedVersion", project.version.toString())
}

tasks.shadowJar {
    // Keep the default "all" classifier so the shaded jar never collides with the thin jar used for compilation.
    configurations.empty().add(relocatedSerialization)
    relocate("kotlinx.serialization", relocatedPackage)

    // The Kotlin standard library and its annotations are provided by MPS and by the consuming distributions; bundling
    // them would shadow those copies on the daemon's flat classpath.
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*:.*"))
        exclude(dependency("org.jetbrains:annotations:.*"))
    }

    duplicatesStrategy = DuplicatesStrategy.WARN
}

// Compile against the plain protocol classes, but run against the shaded jar with the relocated runtime bundled in.
configurations.runtimeElements {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.shadowJar)
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
