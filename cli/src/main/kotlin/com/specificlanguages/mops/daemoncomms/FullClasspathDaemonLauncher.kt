package com.specificlanguages.mops.daemoncomms

import com.specificlanguages.mops.launcher.MpsLaunchArgs.getJvmArgsFor
import com.specificlanguages.mops.protocol.*
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.*
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
    private val startupTimeout: Duration = startupTimeoutFromEnvironment(System.getenv(STARTUP_TIMEOUT_ENV)),
    private val javaOptions: List<String> = javaOptionsFromEnvironment(System.getenv(JAVA_OPTIONS_ENV)),
    private val distribution: DaemonDistribution = DaemonDistribution(),
    private val testSystemCache: Path =
        System.getenv(TEST_SYSTEM_CACHE_ENV)?.takeIf(String::isNotEmpty)?.let(Path::of) ?: records.paths.testSystemCache,
) : DaemonLauncher {

    override fun connectToExistingDaemon(record: DaemonRecord): DefaultDaemonClient {
        return DefaultDaemonClient(port = record.port, token = record.token, timeout = REQUEST_TIMEOUT, daemonPid = record.pid)
    }

    override fun startDaemon(context: DaemonContext): DefaultDaemonClient {
        val token = UUID.randomUUID().toString()
        val workspace = records.paths.workspace(context.realProjectPath)
        val runtimeClasspath = runtimeClasspath(context.realMpsHome)
        val mpsPlugin = distribution.mpsPlugin()

        workspace.createDirectories()
        val classpathArgFile = writeClasspathArgFile(workspace.daemonWorkingDir(), runtimeClasspath)
        try {
            val logFile = workspace.logFile()
            val process = ProcessBuilder(daemonCommand(context, workspace, mpsPlugin, classpathArgFile, token))
                .directory(workspace.daemonWorkingDir().toFile())
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .start()
            return connectToStartedDaemon(process, context, token, workspace)
        } finally {
            Files.deleteIfExists(classpathArgFile)
        }
    }

    /** Joins the daemon distribution and the MPS runtime of [mpsHome] into the daemon JVM classpath. */
    private fun runtimeClasspath(mpsHome: Path): String {
        val mpsClasspath = MpsProductInfo.runtimeClasspath(mpsHome)
        return (distribution.daemonClasspath(mpsClasspath) + mpsClasspath).joinToString(File.pathSeparator)
    }

    private fun writeClasspathArgFile(workDir: Path, runtimeClasspath: String): Path {
        val classpathArgFile = Files.createTempFile(workDir, "daemon-classpath-", ".args")
        Files.writeString(
            classpathArgFile,
            "-cp\n\"${runtimeClasspath.replace("\\", "\\\\").replace("\"", "\\\"")}\"\n",
            Charset.defaultCharset(),
        )
        return classpathArgFile
    }

    private fun daemonCommand(
        context: DaemonContext,
        workspace: DaemonWorkspace,
        mpsPlugin: Path,
        classpathArgFile: Path,
        token: String,
    ): List<String> = buildList {
        add(javaExecutableFromJavaHome(context.realJavaHome).pathString)
        addAll(getJvmArgsFor(context.realMpsHome))
        addAll(MpsProductInfo.bootstrapJvmArgs(context.realMpsHome))
        // The daemon calls native code to detach from the launching session or console.
        add("--enable-native-access=ALL-UNNAMED")
        // IdeaEnvironment otherwise forces plugin modules onto the application classpath.
        add("-Didea.force.use.core.classloader=false")
        add("-Didea.config.path=${workspace.ideaConfigDir()}")
        add("-Didea.system.path=${workspace.ideaSystemDir()}")
        addAll(javaOptions)
        add("@${classpathArgFile.pathString}")
        add("com.specificlanguages.mops.daemon.MainKt")
        add("--project-path")
        add(context.realProjectPath.pathString)
        add("--workspace-path")
        add(workspace.path.pathString)
        add("--mps-home")
        add(context.realMpsHome.pathString)
        add("--mps-plugin")
        add(mpsPlugin.pathString)
        add("--test-system-cache")
        add(testSystemCache.toAbsolutePath().pathString)
        add("--token")
        add(token)
        context.macros.forEach { (name, path) ->
            add("--macro")
            add("$name=$path")
        }
    }

    /**
     * Waits until the started daemon [process] writes its record and answers a ping. Kills the process when it does
     * not.
     */
    private fun connectToStartedDaemon(
        process: Process,
        context: DaemonContext,
        token: String,
        workspace: DaemonWorkspace,
    ): DefaultDaemonClient {
        var startupSucceeded = false
        try {
            process.outputStream.close()
            val record = waitForDaemonRecord(process, context, token, workspace.logFile(), workspace)
            val client = connectToExistingDaemon(record)

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
        val timeoutMillis = startupTimeout.toMillis()
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
        throw daemonStartupException(
            "timed out waiting for daemon project record after ${startupTimeout.seconds} seconds; " +
                "increase $STARTUP_TIMEOUT_ENV to allow more startup time",
            logPath,
        )
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

    companion object {
        private const val STARTUP_TIMEOUT_ENV = "MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS"
        private const val JAVA_OPTIONS_ENV = "MOPS_DAEMON_JAVA_OPTIONS"
        private const val TEST_SYSTEM_CACHE_ENV = "MOPS_TEST_SYSTEM_CACHE"
        private val REQUEST_TIMEOUT = Duration.ofMinutes(2)

        internal fun startupTimeoutFromEnvironment(value: String?): Duration {
            if (value == null) return Duration.ofMinutes(5)
            val seconds = value.toLongOrNull()
            require(seconds != null && seconds in 1..Long.MAX_VALUE / 1000) {
                "$STARTUP_TIMEOUT_ENV must be a positive whole number of seconds no greater than ${Long.MAX_VALUE / 1000}"
            }
            return Duration.ofSeconds(seconds)
        }

        /** Splits [value] on whitespace into extra daemon JVM options; quoting is not supported. */
        internal fun javaOptionsFromEnvironment(value: String?): List<String> =
            value?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()

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
    }
}
