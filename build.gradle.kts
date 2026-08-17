import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.isSymbolicLink

plugins {
    base
    kotlin("jvm") version "2.3.21" apply false
}

group = "com.specificlanguages.mops"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.check {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.build {
    dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.register("verifyReleaseVersion") {
    group = "verification"
    description = "Checks that the project version is a release and matches -PreleaseVersion."

    val expectedVersion = providers.gradleProperty("releaseVersion")
    inputs.property("releaseVersion", expectedVersion)
    inputs.property("projectVersion", project.version.toString())

    doLast {
        val expected = expectedVersion.orNull
            ?: throw GradleException("Specify the release version with -PreleaseVersion=<version>.")
        val actual = project.version.toString()

        if (actual.endsWith("-SNAPSHOT")) {
            throw GradleException("Project version $actual is a snapshot; create a release commit first.")
        }
        if (actual != expected) {
            throw GradleException("Project version $actual does not match release version $expected.")
        }
    }
}

tasks.register<Sync>("installMops") {
    group = "distribution"
    description = "Install the mops CLI into ~/.local/share/mops and symlink ~/.local/bin/mops."
    dependsOn(":cli:installDist")

    val localShareDir = Path.of(System.getProperty("user.home"), ".local", "share", "mops")
    val cliInstallDist = evaluationDependsOn(":cli").tasks.named<Sync>("installDist")
    val installedCli = cliInstallDist.map { it.destinationDir }
    val sharedInstall = localShareDir.resolve("current")
    val installedExecutable = sharedInstall.resolve("bin/mops")
    val binLink = Path.of(System.getProperty("user.home"), ".local", "bin").resolve("mops")

    val localBinDir = Path.of(System.getProperty("user.home"), ".local", "bin")

    from(installedCli)
    into(sharedInstall)
    outputs.file(binLink)

    doFirst {
        val installedCliPath = installedCli.get()
        require(installedCliPath.isDirectory) {
            "CLI distribution not found at $installedCliPath; run :cli:installDist first"
        }

        localShareDir.createDirectories()
        localBinDir.createDirectories()

        if (sharedInstall.isSymbolicLink()) {
            sharedInstall.deleteExisting()
        }
    }

    doLast {
        Files.deleteIfExists(binLink)
        Files.createSymbolicLink(binLink, installedExecutable)
    }
}
