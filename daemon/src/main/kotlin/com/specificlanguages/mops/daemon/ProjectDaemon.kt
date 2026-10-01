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
import jetbrains.mps.core.platform.Platform
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    @Volatile
    var done = false

    /**
     * Runs a loop that listens for incoming socket connections, processes requests, and shuts down upon a timeout or
     * receiving a stop signal.
     *
     * @param mpsAccess Provides read and write access for operations requiring MPS context.
     */
    fun serve(mpsAccess: MpsAccess, platform: Platform? = null) {
        logger.log("environment ready for project ${projectPath.pathString}")

        // Load the lifecycle request classes now, while the classloader is healthy, so a later stop is always
        // decodable even if the platform is mid-teardown by then.
        ProtocolJson.warmUpRequestCodec()

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

            val handler = DomainRequestHandler(workspace.path, mpsAccess, platform)
            val connections = Executors.newCachedThreadPool()
            val active = AtomicInteger()
            val lastActivity = AtomicLong(System.nanoTime())
            val idleNanos = idleTimeout.toNanos()
            server.soTimeout = idleTimeout.toMillis().coerceIn(1, 1000).toInt()

            try {
                while (!done) {
                    val socket = try {
                        server.accept()
                    } catch (_: SocketTimeoutException) {
                        if (idleNanos > 0 && active.get() == 0 && System.nanoTime() - lastActivity.get() >= idleNanos) {
                            logger.log("idle for ${idleTimeout.toMinutes()} min with no requests, shutting down")
                            break
                        }
                        continue
                    } catch (exception: SocketException) {
                        if (done) break
                        throw exception
                    }
                    active.incrementAndGet()
                    connections.execute {
                        try {
                            socket.use {
                                // Bound clients that connect without completing a request so shutdown can drain them.
                                socket.soTimeout = 10_000
                                connection(socket, handler) {
                                    done = true
                                    server.close()
                                }
                            }
                        } catch (throwable: Throwable) {
                            logger.log("request handling failed, continuing to serve: $throwable")
                        } finally {
                            lastActivity.set(System.nanoTime())
                            active.decrementAndGet()
                        }
                    }
                }
            } finally {
                server.close()
                connections.shutdown()
                // Accepted requests finish before the caller disposes the MPS environment.
                var interrupted = false
                while (!connections.isTerminated) {
                    try {
                        connections.awaitTermination(1, TimeUnit.SECONDS)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
                if (interrupted) Thread.currentThread().interrupt()
                // Remove our own record so the next CLI invocation starts a fresh daemon instead of tripping over a
                // dangling record, pinging a dead port, and reporting the failure.
                workspace.deleteDaemonRecordOwnedBy(token)
            }
        }
    }

    private fun connection(socket: Socket, handler: DomainRequestHandler, stop: () -> Unit) {
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
            } catch (throwable: Throwable) {
                // Report any decode failure to the client rather than dropping the connection. This includes linkage
                // errors (an Error, not a RuntimeException) that can surface when a request class is first loaded while
                // the platform is shutting down.
                return@run errorResponse(
                    "INVALID_REQUEST",
                    invalidRequestMessage(throwable)
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
                else -> handler.handleDomainRequest(request)
            }
        }

        try {
            PrintWriter(socket.getOutputStream(), true).use { writer ->
                writer.println(ProtocolJson.encodeResponse(response))
            }
        } finally {
            if (response is StoppedResponse) stop()
        }
    }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspace.path.pathString)

    private fun invalidRequestMessage(throwable: Throwable): String =
        throwable.message ?: "request must be one newline-delimited JSON object"

}
