plugins {
    id("mops.kotlin-jvm-conventions")
    application

    alias(libs.plugins.mps.platform.cache)
    alias(libs.plugins.jbr.toolchain)
}

val integrationTestMps by configurations.registering {
    isCanBeConsumed = false
}

val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val daemonRuntimeClasspath by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true
}

// Resolves the shaded protocol jar and its runtime so the schema generator can run the descriptor walker.
val editSchemaGeneratorClasspath by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    implementation(project(":launcher"))
    implementation(project(":protocol"))
    implementation(libs.picocli)
    implementation(libs.kotlinx.serialization.json)

    integrationTestMps(libs.mps.distribution)
    jbr(libs.mps.jbr)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.system.lambda)
    // Test-only guard that the generated schema matches what the serializer accepts. Never shipped in the CLI runtime.
    testImplementation(libs.json.schema.validator)
    testRuntimeOnly(libs.junit.platform.launcher)

    editSchemaGeneratorClasspath(project(":protocol"))
    daemonRuntimeClasspath(project(":daemon"))
}

configurations.named(integrationTest.implementationConfigurationName) {
    extendsFrom(
        configurations.implementation.get(),
        configurations.testImplementation.get(),
    )
}

configurations.named(integrationTest.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.runtimeOnly.get())
}

application {
    applicationName = "mops"
    mainClass = "com.specificlanguages.mops.cli.MainKt"
}

// Generates model-edit.schema.json into the CLI's resources at build time, so the schema is never hand-maintained or
// checked in. The output dir is wired into main resources, landing the file at the jar root and on the test classpath.
val generateEditSchema by tasks.registering(JavaExec::class) {
    val outputDir = layout.buildDirectory.dir("generated/edit-schema")
    outputs.dir(outputDir)
    classpath = editSchemaGeneratorClasspath.get()
    mainClass = "com.specificlanguages.mops.protocol.GenerateEditSchemaKt"
    argumentProviders.add {
        listOf(outputDir.get().file("model-edit.schema.json").asFile.absolutePath)
    }
}

sourceSets.main {
    resources.srcDir(generateEditSchema)
}

val integrationTestMpsRoot = mpsPlatformCache.getMpsRoot(integrationTestMps)
val integrationTestJbr = jbrToolchain.javaLauncher

val writeDaemonClasspath by tasks.registering {
    val outputFile = layout.buildDirectory.file("generated/daemon-classpath/mops-daemon.classpath")
    inputs.files(daemonRuntimeClasspath)
    outputs.file(outputFile)

    doLast {
        val entries = daemonRuntimeClasspath.get().files
            .map { "lib/${it.name}" }
            .toSortedSet()
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(entries.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
    }
}

distributions {
    main {
        contents {
            into("lib") {
                from(daemonRuntimeClasspath)
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
            into("lib") {
                from(writeDaemonClasspath)
            }
        }
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs CLI integration tests against a daemon started with downloaded MPS and JBR distributions."

    inputs.files(daemonRuntimeClasspath)
        .withPropertyName("daemonRuntimeClasspath")
        .withNormalizer(ClasspathNormalizer::class)

    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath

    jvmArgumentProviders.add {
        listOf(
            "-Dtest.mpsHome=${integrationTestMpsRoot.get()}",
            "-Dtest.jbrHome=${integrationTestJbr.get().metadata.installationPath}",
            "-Dtest.projectsDir=${rootDir.resolve("test-projects")}",
            "-Dmops.daemon.classpath=${daemonRuntimeClasspath.get().asPath}"
        )
    }
}

tasks.check {
    dependsOn("integrationTest")
    dependsOn("gradleDiscoveryTest")
}

tasks.test {
    useJUnitPlatform { excludeTags("gradle-discovery") }
}

tasks.register<Test>("gradleDiscoveryTest") {
    description = "Tests runtime discovery using real Gradle plugin fixtures, without an MPS daemon."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("gradle-discovery") }
    systemProperty("test.repoRoot", rootDir.absolutePath)
}

tasks.named<JavaExec>("run") {
    dependsOn(daemonRuntimeClasspath)
    doFirst {
        systemProperty("mops.daemon.classpath", daemonRuntimeClasspath.get().asPath)
    }
}
