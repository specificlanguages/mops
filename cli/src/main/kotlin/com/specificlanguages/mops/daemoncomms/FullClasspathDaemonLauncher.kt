package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.launcher.MpsLaunchArgs.getJvmArgsFor
import com.specificlanguages.mops.protocol.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.*
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readText

/**
 * Production daemon launcher used by CLI commands. Computes the full classpath for the daemon JVM and starts it.
 *
 * It reuses a compatible project daemon when possible, deletes stale records, and otherwise starts a new JVM with the
 * daemon distribution plus the selected MPS runtime classpath. Startup is complete only after the child process writes
 * a compatible daemon record and responds to an authenticated ping.
 */
class FullClasspathDaemonLauncher(
    private val records: DaemonRecordStore,
    private val timeout: Duration = Duration.ofMinutes(2),
    private val applicationHome: Path? = discoverApplicationHome(),
) : DaemonLauncher {

    override fun connectToExistingDaemon(record: DaemonRecord): DefaultDaemonClient {
        return DefaultDaemonClient(port = record.port, token = record.token, timeout = timeout)
    }

    override fun startDaemon(context: DaemonContext): DefaultDaemonClient {
        val token = UUID.randomUUID().toString()

        val workspace = records.paths.workspace(context.realProjectPath)

        val workDir = workspace.daemonWorkingDir()
        val logFile = workspace.logFile()
        val launchJvmArgs = getJvmArgsFor(context.realMpsHome)

        val runtimeClasspath = listOf(
            daemonClasspath(),
            mpsRuntimeClasspath(context.realMpsHome),
        )
            .filter { it.isNotBlank() }
            .joinToString(File.pathSeparator)

        val processBuilder = ProcessBuilder(
            buildList {
                add(javaExecutableFromJavaHome(context.realJavaHome).pathString)
                addAll(launchJvmArgs)
                add("-Didea.config.path=${workspace.ideaConfigDir()}")
                add("-Didea.system.path=${workspace.ideaSystemDir()}")
                add("-cp")
                add(runtimeClasspath)
                add("com.specificlanguages.mops.daemon.MainKt")
                add("--project-path")
                add(context.realProjectPath.pathString)
                add("--workspace-path")
                add(workspace.path.pathString)
                add("--mps-home")
                add(context.realMpsHome.pathString)
                add("--token")
                add(token)
            })
            .directory(workDir.toFile())
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
            .redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()))

        workspace.createDirectories()

        val process = processBuilder.start()

        var startupSucceeded = false
        try {
            val record = waitForDaemonRecord(process, context, token, logFile, workspace)
            val client = DefaultDaemonClient(port = record.port, token = record.token, timeout = timeout)

            client.ping() // throws on error

            startupSucceeded = true
            return client
        } finally {
            if (!startupSucceeded && process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private fun waitForDaemonRecord(
        process: Process,
        context: DaemonContext,
        token: String,
        logPath: Path,
        workspace: DaemonWorkspace,
    ): DaemonRecord {
        val timeoutMillis = timeout.toMillis()
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            val record = records.read(context.realProjectPath)?.record
            if (record?.context == context && record.token == token) {
                return record
            }

            if (!process.isAlive) {
                throw daemonStartupException(
                    "daemon exited before writing its project record",
                    logPath,
                    workspaceLockDiagnostic(workspace, ownPid = process.pid()),
                )
            }
            Thread.sleep(25)
        }
        throw daemonStartupException("timed out waiting for daemon project record", logPath)
    }

    /**
     * When a foreign, still-running process holds the MPS workspace lock, that is the usual reason a fresh daemon dies
     * during startup: the platform cannot lock the config directory a lingering daemon still owns. Naming that process
     * turns an opaque "exited before writing its record" into an actionable message.
     */
    private fun workspaceLockDiagnostic(workspace: DaemonWorkspace, ownPid: Long): String? =
        workspaceLockHolderMessage(workspace.ideaConfigDir(), ownPid) { pid ->
            ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
        }

    private fun daemonStartupException(
        message: String,
        logPath: Path,
        diagnostic: String? = null,
    ): IllegalStateException {
        val detail = if (diagnostic != null) "$message ($diagnostic)" else message
        return IllegalStateException("$detail. Daemon log: ${logPath.pathString}")
    }

    private fun javaExecutableFromJavaHome(javaHome: Path): Path =
        javaHome.resolve(
            Path.of(
                "bin",
                if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
            )
        )

    private fun daemonClasspath(): String = System.getProperty("mops.daemon.classpath") ?: distributionDaemonClasspath()

    private fun distributionDaemonClasspath(): String {
        val home = applicationHome
            ?: throw IllegalStateException("Could not determine application home, set mops.daemon.classpath JVM property explicitly")
        val classpathFile = home.resolve("lib").resolve(DAEMON_CLASSPATH_FILE)
        if (!Files.isRegularFile(classpathFile)) {
            throw IllegalStateException("File lib/${DAEMON_CLASSPATH_FILE} is missing, set mops.daemon.classpath JVM property explicitly")
        }

        return Files.readAllLines(classpathFile)
            .asSequence()
            .map { it.substringBefore("#").trim() }
            .filter { it.isNotEmpty() }
            .map { entry -> home.resolve(entry) }
            .joinToString(File.pathSeparator) { it.normalize().pathString }
    }

    private fun mpsRuntimeClasspath(mpsHome: Path): String =
        buildList {
            addAll(jarsIn(mpsHome.resolve("lib")))
            addAll(jarsIn(mpsHome.resolve("lib/modules")))
        }.joinToString(File.pathSeparator)

    private fun jarsIn(directory: Path): List<String> {
        if (!Files.isDirectory(directory)) {
            return emptyList()
        }
        return Files.list(directory).use { entries ->
            entries
                .filter { Files.isRegularFile(it) && it.extension == "jar" }
                .map { it.pathString }
                .sorted()
                .toList()
        }
    }

    companion object {
        private const val DAEMON_CLASSPATH_FILE = "mops-daemon.classpath"

        /**
         * Builds a diagnostic when the IntelliJ workspace lock in [configDir] is held by a live process other than
         * [ownPid], returning null when there is no lock, the lock is ours, or its holder is gone. [isAlive] decides
         * whether a pid is a running process, injected so the logic is testable without a real orphan.
         */
        internal fun workspaceLockHolderMessage(
            configDir: Path,
            ownPid: Long,
            isAlive: (Long) -> Boolean,
        ): String? {
            val lockFile = configDir.resolve(".lock")
            if (!lockFile.isRegularFile()) {
                return null
            }
            // IntelliJ's DirectoryLock writes the owning pid as the first whitespace-delimited token of the file.
            val holderPid = runCatching { lockFile.readText() }.getOrNull()
                ?.trim()
                ?.substringBefore(' ')
                ?.substringBefore('\n')
                ?.toLongOrNull()
                ?: return null

            if (holderPid == ownPid || !isAlive(holderPid)) {
                return null
            }

            return "another process (pid $holderPid) still holds the MPS workspace lock at $configDir, " +
                "likely an earlier daemon that did not exit; stop it with `mops daemon stop` or `kill $holderPid`, " +
                "then retry"
        }

        private fun discoverApplicationHome(): Path? {
            val location = FullClasspathDaemonLauncher::class.java.protectionDomain.codeSource?.location
                ?: return null
            val codeSource = runCatching { Path.of(location.toURI()).toAbsolutePath().normalize() }.getOrNull()
                ?: return null
            val libDir =
                if (Files.isRegularFile(codeSource)) codeSource.parent
                else codeSource
            return libDir?.parent
        }
    }
}
