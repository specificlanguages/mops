package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRequest
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.DaemonWorkspace
import com.specificlanguages.mops.protocol.StoredDaemonRecord
import com.specificlanguages.mops.protocol.FindInstancesRequest
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.PingRequest
import com.specificlanguages.mops.protocol.PongResponse
import com.specificlanguages.mops.daemon.core.MpsWrite
import com.specificlanguages.mops.daemon.core.ResolvedScope
import com.specificlanguages.mops.protocol.StopRequest
import com.specificlanguages.mops.protocol.StoppedResponse
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the MPS-free socket loop in [ProjectDaemon], driven over a real loopback socket with
 * an in-memory [MpsAccess]. No MPS boot is triggered, so these run in milliseconds.
 */
class ProjectDaemonSocketTest {
    @TempDir
    lateinit var tempDir: Path

    private val operations = mock<MpsWrite>()
    private var running: RunningDaemon? = null

    @AfterTest
    fun tearDown() {
        running?.close()
    }

    @Test
    fun `ping with the daemon token returns pong metadata`() {
        val daemon = start()

        val response = daemon.exchange(PingRequest(TOKEN))

        val pong = assertIs<PongResponse>(response)
        assertEquals(daemon.projectPath.pathString, pong.projectPath)
        assertEquals(daemon.mpsHome.pathString, pong.mpsHome)
        assertEquals(daemon.workspacePath.pathString, pong.workspacePath)
    }

    @Test
    fun `stop with the daemon token acknowledges and terminates the loop`() {
        val daemon = start()

        val response = daemon.exchange(StopRequest(TOKEN))

        assertIs<StoppedResponse>(response)
        assertTrue(daemon.awaitTermination(), "daemon loop should exit after a stop request")
    }

    @Test
    fun `a request carrying the wrong token is rejected`() {
        val daemon = start()

        val response = daemon.exchange(PingRequest("wrong-token"))

        assertEquals(error("TOKEN_MISMATCH", "invalid daemon token: wrong-token"), response)
    }

    @Test
    fun `a domain request is delegated to the request handler`() {
        val expected = FindInstancesResponse(limit = 100, truncated = false, nodes = emptyList())
        whenever(operations.resolveScope(null)).thenReturn(ResolvedScope.EditableProjectSources)
        whenever(operations.findInstances("some.Concept", false, 100, ResolvedScope.EditableProjectSources))
            .thenReturn(expected)
        val daemon = start()

        val response = daemon.exchange(
            FindInstancesRequest(TOKEN, concept = "some.Concept", exact = false, limit = 100),
        )

        assertEquals(expected, response)
        verify(operations).findInstances("some.Concept", false, 100, ResolvedScope.EditableProjectSources)
    }

    @Test
    fun `a malformed request line is reported as an invalid request`() {
        val daemon = start()

        val response = daemon.exchangeRawParsed("not json")

        assertEquals(error("INVALID_REQUEST", "request must be one newline-delimited JSON object"), response)
    }

    @Test
    fun `the daemon keeps serving after a request it cannot handle`() {
        val daemon = start()

        val failed = daemon.exchangeRawParsed("not json")
        assertEquals(error("INVALID_REQUEST", "request must be one newline-delimited JSON object"), failed)

        // A single unhandled request must not take the loop down: the daemon still answers the next request.
        val pong = daemon.exchange(PingRequest(TOKEN))
        assertIs<PongResponse>(pong)
    }

    @Test
    fun `a request without a type is reported with the discriminator message`() {
        val daemon = start()

        val response = daemon.exchangeRawParsed("""{"token":"$TOKEN"}""")

        assertEquals(error("INVALID_REQUEST", "request type is required"), response)
    }

    @Test
    fun `an unknown request type is reported with the discriminator message`() {
        val daemon = start()

        val response = daemon.exchangeRawParsed("""{"type":"teleport","token":"$TOKEN"}""")

        assertEquals(error("INVALID_REQUEST", "unsupported request type teleport"), response)
    }

    @Test
    fun `an empty request line is reported as an invalid request`() {
        val daemon = start()

        val response = daemon.exchangeWithoutRequest()

        assertEquals(error("INVALID_REQUEST", "request must be one newline-delimited JSON object"), response)
    }

    @Test
    fun `the loop shuts down after the idle timeout with no connections`() {
        val daemon = start(idleTimeout = Duration.ofMillis(200))

        assertTrue(daemon.awaitTermination(), "daemon loop should exit after the idle timeout elapses")
    }

    @Test
    fun `a stopped daemon removes its own record so the next run does not trip over it`() {
        val daemon = start()
        assertTrue(daemon.hasRecord(), "the running daemon should have published its record")

        daemon.exchange(StopRequest(TOKEN))

        assertTrue(daemon.awaitTermination(), "daemon loop should exit after a stop request")
        assertNull(daemon.readRecord(), "a stopped daemon must delete its own record")
    }

    @Test
    fun `an idle daemon removes its own record on shutdown`() {
        val daemon = start(idleTimeout = Duration.ofMillis(200))

        assertTrue(daemon.awaitTermination(), "daemon loop should exit after the idle timeout elapses")
        assertNull(daemon.readRecord(), "an idle-timed-out daemon must delete its own record")
    }

    @Test
    fun `a daemon leaves a record written by a newer daemon in place`() {
        val daemon = start()
        // Simulate a newer daemon taking over the same project by overwriting the record with a different token.
        DaemonWorkspace(daemon.workspacePath).writeDaemonRecord(
            daemon.record().copy(token = "newer-daemon-token"),
        )

        daemon.exchange(StopRequest(TOKEN))
        assertTrue(daemon.awaitTermination(), "daemon loop should exit after a stop request")

        assertEquals(
            "newer-daemon-token",
            daemon.readRecord()?.record?.token,
            "a daemon must not delete a record another daemon now owns",
        )
    }

    private fun start(idleTimeout: Duration = Duration.ofSeconds(30)): RunningDaemon {
        val projectPath = tempDir.resolve("project").createDirectories()
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val workspacePath = tempDir.resolve("workspace")

        val daemon = ProjectDaemon(
            logger = DaemonLogger(),
            projectPath = projectPath,
            workspace = DaemonWorkspace(workspacePath),
            mpsHome = mpsHome,
            token = TOKEN,
            idleTimeout = idleTimeout,
        )

        val thread = Thread({ daemon.serve(mpsAccessOver(operations)) }, "test-daemon").apply {
            isDaemon = true
            start()
        }

        val port = awaitPort(DaemonWorkspace(workspacePath))
        return RunningDaemon(daemon, thread, port, projectPath, mpsHome, workspacePath)
            .also { running = it }
    }

    private fun awaitPort(workspace: DaemonWorkspace): Int {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            workspace.readDaemonRecord()?.let { return it.record.port }
            Thread.sleep(20)
        }
        throw AssertionError("daemon did not publish its record within the timeout")
    }

    private fun error(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = running!!.workspacePath.pathString)

    private class RunningDaemon(
        private val daemon: ProjectDaemon,
        private val thread: Thread,
        private val port: Int,
        val projectPath: Path,
        val mpsHome: Path,
        val workspacePath: Path,
    ) : AutoCloseable {

        fun exchange(request: DaemonRequest): DaemonResponse =
            parse(exchangeRaw(ProtocolJson.encodeRequest(request)))

        fun exchangeRawParsed(requestLine: String): DaemonResponse =
            parse(exchangeRaw(requestLine))

        fun exchangeRaw(requestLine: String): String =
            connect().use { socket ->
                PrintWriter(socket.getOutputStream(), true).use { writer ->
                    writer.println(requestLine)
                    readLine(socket)
                }
            }

        /** Connects and closes the write half without sending, so the daemon reads no request line. */
        fun exchangeWithoutRequest(): DaemonResponse =
            connect().use { socket ->
                socket.shutdownOutput()
                parse(readLine(socket))
            }

        fun awaitTermination(): Boolean {
            thread.join(5_000)
            return !thread.isAlive
        }

        fun readRecord(): StoredDaemonRecord? = DaemonWorkspace(workspacePath).readDaemonRecord()

        fun hasRecord(): Boolean = readRecord() != null

        fun record(): DaemonRecord =
            readRecord()?.record ?: throw AssertionError("daemon has not published a record")

        override fun close() {
            if (thread.isAlive) {
                runCatching { exchange(StopRequest(TOKEN)) }
                thread.join(5_000)
            }
        }

        private fun connect(): Socket =
            Socket(InetAddress.getLoopbackAddress(), port).apply { soTimeout = 5_000 }

        private fun readLine(socket: Socket): String =
            BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                ?: throw AssertionError("daemon closed the connection without a response")

        private fun parse(responseLine: String): DaemonResponse =
            ProtocolJson.decodeResponse(responseLine)
    }

    private companion object {
        const val TOKEN = "test-token"
    }
}
