package com.specificlanguages.mops.cli.homes

import kotlinx.serialization.json.*
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.*

internal class GradleHomeDiscovery {
    private class FileDiscoverer(start: Path) {
        private val ancestors = generateSequence(start) { it.parent }

        fun findDirectoryContaining(vararg regularFileNames: String): Path? =
            ancestors.firstOrNull { dir -> regularFileNames.any { dir.resolve(it).isRegularFile() } }
    }

    fun discover(start: Path, diagnostics: PrintWriter): HomeGuess? {
        val directory = start.toRealPath()
        require(directory.isDirectory()) { "Discovery path is not a directory: $directory" }

        val discoverer = FileDiscoverer(directory)

        val root = discoverer.findDirectoryContaining("settings.gradle", "settings.gradle.kts")
            ?: discoverer.findDirectoryContaining("build.gradle", "build.gradle.kts")
            ?: error("No Gradle build found at or above $directory.")
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val wrapperName = if (isWindows) "gradlew.bat" else "gradlew"
        val wrapper = FileDiscoverer(root).findDirectoryContaining(wrapperName)?.resolve(wrapperName)
            ?: error("No Gradle wrapper found at or above $root. Add a wrapper to the build before running mops create-launcher.")

        val temporary = Files.createTempDirectory("mops-guess-command-line-")
        try {
            val script = temporary.resolve("probe.gradle")
            javaClass.getResourceAsStream("guess-command-line.gradle")!!.use { Files.copy(it, script) }

            val report = temporary.resolve("report.json")
            val task = "mopsGuessCommandLine" + UUID.randomUUID().toString().replace("-", "")
            diagnostics.println("Inspecting Gradle build at $root. Runtime providers may download or extract distributions.")
            diagnostics.flush()
            val processArgs = listOf(
                "--project-dir", root.toString(),
                "--init-script", script.toString(),
                "--no-configuration-cache", "--no-configure-on-demand",
                "--quiet", "--console=plain",
                "-Dmops.guess.root=$root", "-Dmops.guess.task=$task",
                "-Dmops.guess.start=$directory",
                "-Dmops.guess.output=$report", ":$task",
            )
            val command = if (isWindows) listOf("cmd", "/c", wrapper.toString()) else listOf("sh", wrapper.toString())
            val process = ProcessBuilder(command + processArgs)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start()
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { diagnostics.println(it); diagnostics.flush() }
                }
                check(process.waitFor() == 0) { "Gradle runtime discovery failed; see Gradle diagnostics above." }
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
            check(report.isRegularFile()) { "Gradle did not produce a runtime discovery report." }
            return parseReport(report.readText())
        } finally {
            temporary.toFile().deleteRecursively()
        }
    }

    internal fun parseReport(text: String): HomeGuess? {
        val report = Json.parseToJsonElement(text).jsonObject
        require(report.getValue("version").jsonPrimitive.int == 1) { "Unsupported runtime discovery report version." }
        val candidate = report["candidate"]?.takeUnless { it is JsonNull }?.jsonObject ?: return null
        fun path(key: String): Path? = candidate[key]?.jsonPrimitive?.contentOrNull?.let(Path::of)
        return HomeGuess(
            projectDir = path("projectDir")!!,
            buildDir = path("buildDir")!!,
            source = candidate.getValue("source").jsonPrimitive.content,
            mpsHome = path("mpsHome"),
            javaHome = path("javaHome"),
            mpsProjectRoot = path("mpsProjectRoot"),
        )
    }
}
