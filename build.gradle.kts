import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

plugins {
    base
    kotlin("jvm") version "2.3.21" apply false
}

group = "com.specificlanguages.mops"
version = "0.3.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.named("check") {
    dependsOn(subprojects.map { "${it.path}:check" })
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

val localBinDir = Path.of(System.getProperty("user.home"), ".local", "bin")
val localShareDir = Path.of(System.getProperty("user.home"), ".local", "share", "mops")
val installedCli = layout.projectDirectory.dir("cli/build/install/mops")

tasks.register("installMops") {
    group = "distribution"
    description = "Install the mops CLI into ~/.local/share/mops and symlink ~/.local/bin/mops."
    dependsOn(":cli:installDist")

    inputs.dir(installedCli)
    outputs.dir(localShareDir)
    outputs.dir(localBinDir)

    doLast {
        val installed = installedCli.asFile.toPath()
        require(Files.isDirectory(installed)) { "CLI distribution not found at $installed; run :cli:installDist first" }

        localShareDir.createDirectories()
        localBinDir.createDirectories()

        val sharedInstall = localShareDir.resolve("current")
        Files.deleteIfExists(sharedInstall)
        Files.createSymbolicLink(sharedInstall, installed)

        val binLink = localBinDir.resolve("mops")
        Files.deleteIfExists(binLink)
        Files.createSymbolicLink(binLink, sharedInstall.resolve("bin/mops"))
    }
}
