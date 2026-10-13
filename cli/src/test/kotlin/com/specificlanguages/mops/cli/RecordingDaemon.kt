package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch

/**
 * A fake daemon that answers each request with the next prerecorded response and exits once they are used up. Clients
 * open one connection per request, so the server accepts connections until the responses run out. The request trace
 * goes to stderr so tests can inspect the CLI's stdout verbatim.
 */
internal class RecordingDaemon(val responseIterator: Iterator<DaemonResponse>) : Thread() {
    lateinit var server: ServerSocket
    val serverReady = CountDownLatch(1)
    val port get() = server.localPort
    val requestsReceived: MutableList<String> = mutableListOf()

    init {
        // A daemon thread, so a test that records more responses than the CLI requests cannot keep the JVM alive.
        isDaemon = true
    }

    override fun run() {
        server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        server.use {
            serverReady.countDown()
            while (responseIterator.hasNext()) {
                it.accept().use { socket ->
                    BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                        PrintWriter(socket.getOutputStream(), true).use { writer ->
                            while (responseIterator.hasNext()) {
                                val requestLine = reader.readLine() ?: break

                                requestsReceived.add(requestLine)
                                val response = responseIterator.next()

                                writer.println(ProtocolJson.encodeResponse(response))

                                System.err.println("request: $requestLine")
                                System.err.println(" -> response: ${ProtocolJson.encodeResponse(response)}")
                            }
                        }
                    }
                }
            }
        }
    }
}
