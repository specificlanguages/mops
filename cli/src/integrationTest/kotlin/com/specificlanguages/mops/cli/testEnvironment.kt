package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.DaemonRecordStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant.now
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
    var exitCode = Int.MIN_VALUE
    var stderr = ""
    val stdout = tapSystemOut {
        stderr = tapSystemErr {
            exitCode = newCommandLine(workingDirectory = workingDirectory).execute(*args)
        }
    }

    return CliResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
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
    runCommandLine(
        project,
        "--daemon-home",
        daemonHome.pathString,
        "--mps-home", requiredProperty("test.mpsHome"),
        "daemon", "stop",
    )

    waitForAllDaemons(daemonHome)
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
    // MPS 2026.2 packages the Jupiter API stubs in JUnit; parameterized-test stubs remain in org.junit.junit5.
    if (mpsHome.resolve("lib/intellij.libraries.junit5.jar").exists()) {
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
