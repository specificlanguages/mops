package com.specificlanguages.mops.cli

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Runs the installed CLI in its own process, with [environment] added to the test JVM's environment. With
 * [sharedWorkerSystem], its daemons use the test worker system directory of this test JVM instead of the one in their
 * workspace.
 */
class InstalledCli(
    private val project: Path,
    private val home: Path,
    private val workDir: Path,
    private val environment: Map<String, String> = emptyMap(),
    private val sharedWorkerSystem: Boolean = true,
) {
    fun run(vararg args: String): CliResult {
        val install = Path.of(System.getProperty("test.cliInstall"))
        val stdout = workDir.resolve("cli-stdout.txt")
        val stderr = workDir.resolve("cli-stderr.txt")
        val jvmArgs = if (sharedWorkerSystem) testWorkerSystemJvmArgs() else emptyList()
        val process = ProcessBuilder(
            javaExecutable().pathString, *jvmArgs.toTypedArray(), "-cp", install.resolve("lib").pathString + "/*",
            "com.specificlanguages.mops.cli.MainKt", "--project-root", project.pathString,
            "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args,
        ).apply { environment().putAll(environment) }
            .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
        return awaitCli(process, stdout, stderr, home)
    }
}
