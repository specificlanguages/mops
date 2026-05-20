package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.GsonCodec
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch

internal class RecordingDaemon(val responseIterator: Iterator<DaemonResponse>) : Thread() {
    lateinit var server: ServerSocket
    val serverReady = CountDownLatch(1)
    val port get() = server.localPort
    val requestsReceived: MutableList<String> = mutableListOf()

    override fun run() {
        server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        server.use {
            serverReady.countDown()
            it.accept().use { socket ->
                BufferedReader(InputStreamReader(socket.getInputStream())).use { reader ->
                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        do {
                            if (!responseIterator.hasNext()) break

                            val requestLine = reader.readLine() ?: break

                            requestsReceived.add(requestLine)
                            val response = responseIterator.next()

                            GsonCodec.toJson(response, writer)
                            writer.println()

                            println("request: $requestLine")
                            println(" -> response: ${GsonCodec.toJson(response)}")
                        } while (true)
                    }
                }
            }
        }
    }
}
