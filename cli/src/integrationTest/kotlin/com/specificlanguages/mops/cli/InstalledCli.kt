package com.specificlanguages.mops.cli

import java.nio.file.Path
import kotlin.io.path.*

class InstalledCli(private val project: Path, private val home: Path, private val workDir: Path) {
    fun run(vararg args: String): CliResult {
        val install = Path.of(System.getProperty("test.cliInstall"))
        val stdout = workDir.resolve("cli-stdout.txt")
        val stderr = workDir.resolve("cli-stderr.txt")
        val process = ProcessBuilder(
            javaExecutable().pathString, "-cp", install.resolve("lib").pathString + "/*",
            "com.specificlanguages.mops.cli.MainKt", "--project-root", project.pathString,
            "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args,
        ).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
        return awaitCli(process, stdout, stderr, home)
    }
}
