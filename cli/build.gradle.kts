plugins {
    id("mops.kotlin-jvm-conventions")
    application

    id("com.specificlanguages.mps-platform-cache") version "1.0.0"
    id("com.specificlanguages.jbr-toolchain") version "1.0.2"
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

dependencies {
    implementation(project(":launcher"))
    implementation(project(":protocol"))
    implementation("info.picocli:picocli:4.7.7")

    integrationTestMps("com.jetbrains:mps:2025.1.2")
    jbr("com.jetbrains.jdk:jbr_jcef:21.0.8-b895.146")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")

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
    dependsOn(daemonRuntimeClasspath)

    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath

    jvmArgumentProviders.add {
        listOf(
            "-Dtest.mpsHome=${integrationTestMpsRoot.get()}",
            "-Dtest.jbrHome=${integrationTestJbr.get().metadata.installationPath}",
            "-Dmops.daemon.classpath=${daemonRuntimeClasspath.get().asPath}"
        )
    }
}

tasks.named("check") {
    dependsOn("integrationTest")
}

tasks.named<JavaExec>("run") {
    dependsOn(daemonRuntimeClasspath)
    doFirst {
        systemProperty("mops.daemon.classpath", daemonRuntimeClasspath.get().asPath)
    }
}
