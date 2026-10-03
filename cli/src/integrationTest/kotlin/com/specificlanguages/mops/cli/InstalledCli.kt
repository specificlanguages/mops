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
        val output = workDir.resolve("cli-output.txt")
        val process = ProcessBuilder(
            java.pathString, "-cp", install.resolve("lib/*").pathString,
            "com.specificlanguages.mops.cli.MainKt", "--project-root", project.pathString,
            "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args,
        ).redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            assertTrue(process.waitFor(3, TimeUnit.MINUTES), "CLI timed out: ${output.readText()}")
            return CliResult(process.exitValue(), output.readText(), "")
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
