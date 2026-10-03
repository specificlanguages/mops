import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("mops.kotlin-jvm-conventions")
    application

    alias(libs.plugins.mps.platform.cache)
}

val integrationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val daemonRuntimeClasspath by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val daemonMpsPlugin by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
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

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.system.lambda)
    // Test-only guard that the generated schema matches what the serializer accepts. Never shipped in the CLI runtime.
    testImplementation(libs.json.schema.validator)
    testRuntimeOnly(libs.junit.platform.launcher)

    editSchemaGeneratorClasspath(project(":protocol"))
    daemonRuntimeClasspath(project(":daemon"))
    daemonMpsPlugin(project(":daemon-mps-plugin"))
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

tasks.processResources {
    from(rootProject.layout.projectDirectory.dir("skills/mops/references")) {
        into("examples")
    }
}

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
            from(rootProject.layout.projectDirectory.dir("skills")) {
                into("skills")
            }
            into("lib") {
                from(daemonRuntimeClasspath)
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
            into("lib") {
                from(writeDaemonClasspath)
            }
            into("mps-plugins") {
                from(daemonMpsPlugin)
                rename { "mops-daemon-plugin.jar" }
            }
        }
    }
}

fun Test.configureIntegrationTest(mpsHome: Provider<File>, jbrHome: Provider<File>) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    dependsOn(tasks.installDist)
    dependsOn(daemonMpsPlugin)
    inputs.property("mpsHome", mpsHome.map { it.absolutePath })
    inputs.property("jbrHome", jbrHome.map { it.absolutePath })
    inputs.dir(tasks.installDist.map { it.destinationDir })

    inputs.files(daemonRuntimeClasspath)
        .withPropertyName("daemonRuntimeClasspath")
        .withNormalizer(ClasspathNormalizer::class)

    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    testLogging {
        events("started", "passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }

    jvmArgumentProviders.add {
        listOf(
            "-Dtest.mpsHome=${mpsHome.get()}",
            "-Dtest.jbrHome=${jbrHome.get()}",
            "-Dtest.cliInstall=${tasks.installDist.get().destinationDir}",
            "-Dtest.groovyVersion=${libs.versions.groovy.get()}",
            "-Dtest.projectsDir=${rootDir.resolve("test-projects")}",
            "-Dmops.daemon.classpath=${daemonRuntimeClasspath.get().asPath}",
            "-Dmops.daemon.mps.plugin=${daemonMpsPlugin.get().singleFile.absolutePath}"
        )
    }
}

val jbrOs = when {
    System.getProperty("os.name").startsWith("Windows") -> "windows"
    System.getProperty("os.name").startsWith("Mac") -> "osx"
    System.getProperty("os.name").startsWith("Linux") -> "linux"
    else -> error("Unsupported JBR operating system: ${System.getProperty("os.name")}")
}
val jbrArch = when (System.getProperty("os.arch")) {
    "aarch64", "arm64" -> "aarch64"
    "amd64", "x86_64" -> "x64"
    else -> error("Unsupported JBR architecture: ${System.getProperty("os.arch")}")
}

val supportedMpsVersions = providers.gradleProperty("supportedMpsVersions").get().split(',').map(String::trim)
val smokeMpsVersion = supportedMpsVersions.last()
val smokeIntegrationTest = tasks.register<Test>("smokeIntegrationTest") {
    description = "Runs smoke integration tests against MPS $smokeMpsVersion and its matching JBR."
    useJUnitPlatform { includeTags("smoke") }
}

val integrationTests = supportedMpsVersions.map { mpsVersion ->
    val mps = configurations.register("integrationTestMps$mpsVersion") {
        isCanBeConsumed = false
    }
    val jbr = configurations.register("integrationTestJbr$mpsVersion") {
        isCanBeConsumed = false
        resolutionStrategy.dependencySubstitution {
            all {
                val selector = requested
                // The MPS marker selects the JBR version; only the actual JBR archive needs a host classifier.
                if (selector is ModuleComponentSelector && selector.group == "com.jetbrains.jdk") {
                    artifactSelection { selectArtifact("tgz", null, "$jbrOs-$jbrArch") }
                }
            }
        }
    }
    dependencies.add(mps.name, "com.jetbrains:mps:$mpsVersion")
    dependencies.add(jbr.name, "com.jetbrains.mps:mps-jbr:$mpsVersion")
    val mpsHome = mpsPlatformCache.getMpsRoot(mps)
    val jbrHome = mpsPlatformCache.getJbrRoot(jbr).map {
        if (jbrOs == "osx") it.resolve("Contents/Home") else it
    }
    if (mpsVersion == smokeMpsVersion) {
        smokeIntegrationTest.configure { configureIntegrationTest(mpsHome, jbrHome) }
    }
    tasks.register<Test>("integrationTestMps$mpsVersion") {
        description = "Runs CLI integration tests against MPS $mpsVersion and its matching JBR."
        configureIntegrationTest(mpsHome, jbrHome)
    }
}

tasks.register("integrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs CLI integration tests against every supported MPS version."
    dependsOn(integrationTests)
}

tasks.register<Test>("integrationTestLocal") {
    description = "Runs CLI integration tests using -PtestMpsHome and -PtestJbrHome."
    configureIntegrationTest(
        providers.gradleProperty("testMpsHome").map(::file),
        providers.gradleProperty("testJbrHome").map(::file),
    )
}

tasks.check {
    dependsOn("integrationTest")
    dependsOn("gradleDiscoveryTest")
}

tasks.test {
    useJUnitPlatform { excludeTags("gradle-discovery") }
}

val smokeUnitTest by tasks.registering(Test::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs smoke unit tests without starting MPS."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("smoke") }
}

tasks.register("smokeTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs cross-platform CLI smoke unit and integration tests."
    dependsOn(smokeUnitTest, smokeIntegrationTest)
}

tasks.register<Test>("gradleDiscoveryTest") {
    description = "Tests runtime discovery using real Gradle plugin fixtures, without an MPS daemon."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("gradle-discovery") }
    systemProperty("test.repoRoot", rootDir.absolutePath)
}

tasks.named<JavaExec>("run") {
    dependsOn(daemonRuntimeClasspath, daemonMpsPlugin)
    doFirst {
        systemProperty("mops.daemon.classpath", daemonRuntimeClasspath.get().asPath)
        systemProperty("mops.daemon.mps.plugin", daemonMpsPlugin.get().singleFile.absolutePath)
    }
}
