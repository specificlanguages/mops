package com.specificlanguages.mops.cli

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.test.assertTrue

class InstalledCli(private val project: Path, private val home: Path, private val workDir: Path) {
    fun run(vararg args: String): CliResult {
        val install = Path.of(System.getProperty("test.cliInstall"))
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val stdout = workDir.resolve("cli-stdout.txt")
        val stderr = workDir.resolve("cli-stderr.txt")
        val process = ProcessBuilder(
            java.pathString, "-cp", install.resolve("lib").pathString + "/*",
            "com.specificlanguages.mops.cli.MainKt", "--project-root", project.pathString,
            "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args,
        ).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
        try {
            val finished = process.waitFor(3, TimeUnit.MINUTES)
            val result = CliResult(if (finished) process.exitValue() else -1, stdout.readText(), stderr.readText())
            if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
            assertTrue(finished, "CLI timed out: ${result.output}")
            return result
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
