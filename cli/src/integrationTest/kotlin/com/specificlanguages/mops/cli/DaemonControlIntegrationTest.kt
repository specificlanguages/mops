package com.specificlanguages.mops.cli

import org.junit.jupiter.api.Tag
import com.specificlanguages.mops.daemoncomms.DefaultDaemonClient
import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.PongResponse
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.FutureTask
import kotlin.concurrent.thread
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ResourceLock("system-streams")
@Tag("smoke")
class DaemonControlIntegrationTest {

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `discovery pings keep using a daemon during blocked code mode`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        val entered = tempDir.resolve("code-entered")
        val release = tempDir.resolve("code-release")
        var work: FutureTask<*>? = null
        fun literal(path: Path) = "'" + path.pathString.replace("\\", "\\\\").replace("'", "\\'") + "'"
        try {
            val started = runCommandLine(project, *javaAndMpsHomeArgs(),
                "--daemon-home", daemonHome.pathString, "daemon", "ping")
            assertEquals(0, started.exitCode, started.output)
            val record = requireNotNull(store.read(project)).record
            val client = DefaultDaemonClient.fromRecord(record)
            val request = FutureTask {
                client.runCode("""
                    java.nio.file.Files.writeString(java.nio.file.Path.of(${literal(entered)}), 'entered')
                    while (!java.nio.file.Files.exists(java.nio.file.Path.of(${literal(release)}))) {
                        Thread.sleep(20)
                    }
                    return 'finished'
                """.trimIndent(), "blocked-code.groovy", Duration.ofSeconds(60))
            }
            work = request
            thread(isDaemon = true) { request.run() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (!Files.exists(entered) && !request.isDone && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue(Files.exists(entered), "Code Mode must enter the blocked operation")
            val ping = runCommandLine(project, *javaAndMpsHomeArgs(),
                "--daemon-home", daemonHome.pathString, "daemon", "ping")
            assertEquals(0, ping.exitCode, ping.output)
            assertIs<PongResponse>(ProtocolJson.decodeResponse(ping.stdout.trim()))
            assertEquals(record, store.read(project)?.record, "discovery must retain the busy daemon")
            assertFalse(request.isDone, "ping must finish while Code Mode is blocked")
        } finally {
            Files.writeString(release, "release")
            try {
                work?.get(30, TimeUnit.SECONDS)
            } finally {
                stopDaemons(project, daemonHome)
            }
        }
    }

    @Test
    fun `daemon detaches and status detects a killed daemon`() {
        val windows = System.getProperty("os.name").startsWith("Windows")
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        try {
            val ping = runCommandLine(project, *javaAndMpsHomeArgs(),
                "--daemon-home", daemonHome.pathString, "daemon", "ping")
            assertEquals(0, ping.exitCode, ping.output)
            val record = requireNotNull(store.read(project)).record
            // Calls an int-returning native function with int arguments, the same way DaemonProcess does: through the
            // Foreign Function API on Java 22 and newer, otherwise through JNA from the MPS boot classpath. Starting
            // with MPS 2026.3, JNA is no longer on the boot classpath.
            val nativeCall = """
                def windows = $windows
                def call
                if (Runtime.version().feature() >= 22) {
                    def linker = java.lang.foreign.Linker.nativeLinker()
                    def symbols = windows
                        ? java.lang.foreign.SymbolLookup.libraryLookup('kernel32', java.lang.foreign.Arena.global())
                        : linker.defaultLookup()
                    def int32 = java.lang.foreign.ValueLayout.JAVA_INT
                    call = { String name, List<Integer> args ->
                        def descriptor = java.lang.foreign.FunctionDescriptor.of(int32, *([int32] * args.size()))
                        linker.downcallHandle(symbols.find(name).orElseThrow(), descriptor).invokeWithArguments(args) as int
                    }
                } else {
                    def library = com.sun.jna.NativeLibrary.getInstance(windows ? 'kernel32' : 'c')
                    def convention = windows ? com.sun.jna.Function.ALT_CONVENTION : com.sun.jna.Function.C_CONVENTION
                    call = { String name, List<Integer> args ->
                        library.getFunction(name, convention).invokeInt(args as Object[])
                    }
                }
            """.trimIndent()
            val program = tempDir.resolve("process-group.groovy").also {
                it.writeText(nativeCall + "\n" + if (windows) """
                    assert call('GetConsoleCP', []) == 0
                    assert call('AllocConsole', []) != 0
                    assert call('GetConsoleCP', []) != 0
                    com.specificlanguages.mops.daemon.DaemonProcess.INSTANCE.detach()
                    assert call('GetConsoleCP', []) == 0
                    com.specificlanguages.mops.daemon.DaemonProcess.INSTANCE.detach()
                    return [consoleCodePage: call('GetConsoleCP', [])]
                """.trimIndent() else """
                    return [processGroup: call('getpgrp', []), session: call('getsid', [0])]
                """.trimIndent())
            }
            val group = runCommandLine(project, *javaAndMpsHomeArgs(),
                "--daemon-home", daemonHome.pathString, "code", "run", program.pathString)
            assertEquals(0, group.exitCode, group.output)
            if (windows) {
                assertContains(group.stdout, "\"consoleCodePage\":0")
            } else {
                assertContains(group.stdout, "\"processGroup\":${record.pid}")
                assertContains(group.stdout, "\"session\":${record.pid}")
            }

            val running = runCommandLine(project, "--daemon-home", daemonHome.pathString, "daemon", "status")
            assertEquals(0, running.exitCode, running.output)
            assertContains(running.stdout, "running ")

            val handle = ProcessHandle.of(record.pid).orElseThrow()
            handle.destroyForcibly()
            handle.onExit().get(10, TimeUnit.SECONDS)
            val stopped = runCommandLine(project, "--daemon-home", daemonHome.pathString, "daemon", "status")
            assertEquals(0, stopped.exitCode, stopped.output)
            assertContains(stopped.stdout, "unreachable ")
            assertEquals(record, store.read(project)?.record)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `daemon stop removes the current project record`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        val store = DaemonRecordStore.forDaemonHome(daemonHome)

        val ping = runCommandLine(
            project,
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "ping"
        )
        assertEquals(0, ping.exitCode, ping.output)

        val daemonPid = store.read(project)?.record?.pid
            ?: throw AssertionError("a running daemon should have published its record")

        val stop = runCommandLine(
            project,
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "stop"
        )

        assertEquals(0, stop.exitCode, stop.output)
        assertContains(stop.stdout, "stopped")
        assertNull(store.read(project))
        // The daemon must actually terminate, not just drop its record: a surviving process would keep holding the
        // MPS workspace lock and block the next start.
        assertFalse(
            ProcessHandle.of(daemonPid).map { it.isAlive }.orElse(false),
            "daemon process pid=$daemonPid should have exited after stop",
        )
    }

    @Test
    fun `daemon ping output remains a single JSON response after replacing a stale project daemon record`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val staleMpsHome = tempDir.resolve("stale-mps").createDirectories().toRealPath()
        val javaHome = Path.of(System.getProperty("java.home")).toRealPath()

        val store = DaemonRecordStore.forDaemonHome(daemonHome)
        store.write(
            DaemonRecord(
                port = 9,
                token = "stale-token",
                pid = 999_999L,
                daemonVersion = "0.3.0-SNAPSHOT",
                context = DaemonContext.fromLivePaths(
                    projectPath = project,
                    mpsHome = staleMpsHome,
                    javaHome = javaHome,
                ),
                workspace = store.workspacePath(project),
                startupTime = "2026-05-12T12:02:00Z",
            ),
        )

        val ping = runCommandLine(
            project,
            *javaAndMpsHomeArgs(),
            "--daemon-home", daemonHome.pathString,
            "daemon", "ping",
        )

        try {
            assertEquals(0, ping.exitCode, ping.output)

            val response = try {
                ProtocolJson.decodeResponse(ping.stdout)
            } catch (exception: RuntimeException) {
                throw AssertionError(
                    "daemon ping stdout should be parseable as a single JSON response, but was:\n${ping.stdout}",
                    exception,
                )
            }

            assertIs<PongResponse>(response)
            assertEquals(project.toRealPath().pathString, response.projectPath)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

}
