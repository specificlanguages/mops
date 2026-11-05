package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.DaemonPaths
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.DaemonWorkspace
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant.now
import java.util.concurrent.TimeUnit
import kotlin.test.fail
import kotlin.io.path.*
import kotlin.jvm.optionals.getOrNull
import kotlin.use

fun copyTestProject(name: String, target: Path): Path {
    val source = Path.of(requiredProperty("test.projectsDir")).resolve(name)
    require(Files.isDirectory(source)) { "missing test project $source" }
    target.createDirectories()
    copyDirectory(source, target)
    return target
}

private fun copyDirectory(source: Path, target: Path) {
    target.createDirectories()
    Files.walk(source).use { paths ->
        paths.forEach { path ->
            val destination = target.resolve(source.relativize(path).pathString)
            if (Files.isDirectory(path)) {
                destination.createDirectories()
            } else {
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun requiredProperty(name: String): String =
    requireNotNull(System.getProperty(name)) { "missing system property $name" }

public fun javaAndMpsHomeArgs(): Array<String> =
    arrayOf(
        "--java-home",
        requiredProperty("test.jbrHome"),
        "--mps-home",
        requiredProperty("test.mpsHome"),
    )

fun runCommandLine(workingDirectory: Path, vararg args: String): CliResult {
    val started = System.nanoTime()
    var exitCode = Int.MIN_VALUE
    var stderr = ""
    val stdout = tapSystemOut {
        stderr = tapSystemErr {
            exitCode = newCommandLine(workingDirectory = workingDirectory).execute(*args)
        }
    }

    println("MOPS_CLI_TIMING millis=${(System.nanoTime() - started) / 1_000_000} args=${args.joinToString(" ")}")
    return CliResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
}

/** The `java` executable of the JVM running the tests. */
fun javaExecutable(): Path = Path.of(
    System.getProperty("java.home"), "bin",
    if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java",
)

/**
 * Longer than the CLI's default daemon startup budget: five minutes for the daemon to write its record plus two minutes
 * for the first ping. A slow daemon start then fails with the CLI's own startup error instead of being cut off here.
 */
private val CLI_TIMEOUT: Duration = Duration.ofMinutes(8)

/**
 * Waits for a CLI [process] whose output goes to [stdout] and [stderr], and kills it when it does not finish within
 * [timeout]. When the CLI fails or times out, the logs of the daemons under [daemonHome] go to stderr or into the
 * failure message, because the daemon home is a temporary directory that does not outlive the test run.
 */
fun awaitCli(process: Process, stdout: Path, stderr: Path, daemonHome: Path, timeout: Duration = CLI_TIMEOUT): CliResult {
    try {
        val finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        val result = CliResult(if (finished) process.exitValue() else -1, stdout.readText(), stderr.readText())
        if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
        if (!finished) fail("CLI timed out after ${timeout.seconds} seconds: ${result.output}${daemonLogs(daemonHome)}")
        if (result.exitCode != 0) System.err.println(daemonLogs(daemonHome))
        return result
    } finally {
        if (process.isAlive) process.destroyForcibly()
    }
}

private fun daemonLogs(daemonHome: Path): String {
    val projects = DaemonPaths(daemonHome).projects
    val logs = if (projects.isDirectory()) {
        projects.listDirectoryEntries().map { DaemonWorkspace(it).logFile() }.filter { it.isRegularFile() }
    } else {
        emptyList()
    }
    return logs.joinToString("") { "\nDaemon log $it:\n${it.readText()}" }.ifEmpty { "\nNo daemon logs under $daemonHome" }
}

data class CliResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val output: String
        get() = "CLI output:\n$stdout\nCLI error:\n$stderr"
}

fun stopDaemons(project: Path, daemonHome: Path) {
    val started = System.nanoTime()
    runCommandLine(
        project,
        "--daemon-home",
        daemonHome.pathString,
        "--mps-home", requiredProperty("test.mpsHome"),
        "daemon", "stop",
    )

    waitForAllDaemons(daemonHome)
    println("MOPS_DAEMON_STOP_TIMING millis=${(System.nanoTime() - started) / 1_000_000} home=$daemonHome")
    val projects = daemonHome.resolve("projects")
    if (projects.exists()) for (workspace in projects.listDirectoryEntries()) {
        val runs = workspace.resolve("test-runs")
        if (!runs.isDirectory()) continue
        for (run in runs.listDirectoryEntries()) {
            if (!run.resolve("report.json").exists()) continue
            val destination = Path.of("build/test-results/test-run-diagnostics")
                .resolve(Path.of(requiredProperty("test.mpsHome")).fileName)
                .resolve(run.fileName).createDirectories()
            for (name in listOf("report.json", "worker.log", "ant.log")) {
                val source = run.resolve(name)
                if (source.exists()) source.copyTo(destination.resolve(name), overwrite = true)
            }
        }
    }
}

private fun waitForAllDaemons(daemonHome: Path) {
    val recordStore = DaemonRecordStore.forDaemonHome(daemonHome)
    val deadline = now().plusSeconds(10)
    while (now() < deadline) {
        val anyAlive = recordStore.readAll().any { record ->
            val handle = ProcessHandle.of(record.record.pid).getOrNull()
            handle != null && handle.isAlive
        }

        if (anyAlive) {
            Thread.sleep(100)
        } else {
            return
        }
    }
}

fun copyTestingProject(target: Path): Path {
    val project = copyTestProject("testing", target)
    val mpsHome = Path.of(System.getProperty("test.mpsHome"))
    // MPS 2026.2 (junit5 jar) and 2026.3 (junit6 jar) package the Jupiter API stubs in JUnit; parameterized-test stubs
    // remain in org.junit.junit5. MPS 2026.3 discovers @Test methods only when the annotation comes from JUnit.
    if (listOf("junit5", "junit6").any { mpsHome.resolve("lib/intellij.libraries.$it.jar").exists() }) {
        for (name in listOf("ordinary", "slow", "crash")) {
            val model = project.resolve("solutions/tests/models/$name.mps")
            model.writeText(model.readText().replace(
                "63b449db-0918-4a4a-a891-2c430ab133e4/java:org.junit.jupiter.api(org.junit.junit5/)",
                "49808fad-9d41-4b96-83fa-9231640f6b2b/java:org.junit.jupiter.api(JUnit/)",
            ))
        }
    }
    return project
}
