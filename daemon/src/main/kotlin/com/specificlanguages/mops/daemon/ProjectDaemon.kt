package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.DaemonRecord
import com.specificlanguages.mops.protocol.DaemonRequest
import com.specificlanguages.mops.protocol.DaemonWorkspace
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.PingRequest
import com.specificlanguages.mops.protocol.PongResponse
import com.specificlanguages.mops.protocol.StopRequest
import com.specificlanguages.mops.protocol.StoppedResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.pathString

class ProjectDaemon(
    val logger: DaemonLogger,
    val projectPath: Path,
    val workspace: DaemonWorkspace,
    val mpsHome: Path,
    val token: String,
    val idleTimeout: Duration,
) {
    var done = false

    /**
     * Runs a loop that listens for incoming socket connections, processes requests, and shuts down upon a timeout or
     * receiving a stop signal.
     *
     * @param mpsAccess Provides read and write access for operations requiring MPS context.
     */
    fun serve(mpsAccess: MpsAccess) {
        logger.log("environment ready for project ${projectPath.pathString}")

        ServerSocket(/* port = */ 0, /* backlog = */ 10, /* bindAddr = */ InetAddress.getLoopbackAddress()).use { server ->
            logger.log("ready on ${server.inetAddress.hostAddress}:${server.localPort}")

            workspace.writeDaemonRecord(
                record = DaemonRecord(
                    port = server.localPort,
                    token = token,
                    pid = ProcessHandle.current().pid(),
                    daemonVersion = "0.3.0-SNAPSHOT",
                    context = DaemonContext.fromLivePaths(
                        projectPath = projectPath,
                        mpsHome = mpsHome,
                        javaHome = Path.of(System.getProperty("java.home"))
                    ),
                    workspace = workspace.path,
                    startupTime = Instant.now().toString(),
                ),
            )

            server.soTimeout = idleTimeout.toMillis().toInt()

            while (!done) {
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    break
                }
                socket.use {
                    connection(socket, mpsAccess)
                }
            }
        }
    }

    private fun connection(socket: Socket, mpsAccess: MpsAccess) {
        val requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()

        val response = run {
            val request = try {
                if (requestLine == null) {
                    return@run errorResponse(
                        "INVALID_REQUEST",
                        "request must be one newline-delimited JSON object"
                    )
                }
                ProtocolJson.decodeRequest(requestLine)
            } catch (exception: RuntimeException) {
                return@run errorResponse(
                    "INVALID_REQUEST",
                    invalidRequestMessage(exception)
                )
            }

            if (request.token != token) {
                return@run errorResponse("TOKEN_MISMATCH", "invalid daemon token: ${request.token}")
            }

            return@run when (request) {
                is PingRequest -> PongResponse(
                    projectPath = projectPath.pathString,
                    mpsHome = mpsHome.pathString,
                    workspacePath = workspace.path.pathString,
                )

                is StopRequest -> StoppedResponse()
                else -> DomainRequestHandler(workspace.path, mpsAccess).handleDomainRequest(request)
            }
        }

        PrintWriter(socket.getOutputStream(), true).use { writer ->
            writer.println(ProtocolJson.encodeResponse(response))
        }
        if (response is StoppedResponse) {
            done = true
        }
    }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspace.path.pathString)

    private fun invalidRequestMessage(exception: RuntimeException): String =
        exception.message ?: "request must be one newline-delimited JSON object"

}
