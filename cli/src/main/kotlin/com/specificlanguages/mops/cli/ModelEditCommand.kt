package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import java.nio.file.Path
import kotlin.io.path.readText

@Command(name = "edit", description = ["Apply a JSON batch of edit operations."])
class ModelEditCommand(private val daemonClient: DaemonClient? = null) : Runnable {
    @ParentCommand
    lateinit var model: ModelOperations

    @Option(
        names = ["--file"],
        paramLabel = "PATH",
        description = ["Read the edit batch from a JSON file instead of stdin."],
    )
    var file: String? = null

    override fun run() {
        val batch = readBatch()
        require(batch.operations.isNotEmpty()) { "edit batch must contain at least one operation" }

        val client = daemonClient ?: model.root.ensureDaemon()
        val response = client.modelEdit(batch)
        println(ProtocolJson.encodeResponse(response))
    }

    private fun readBatch(): EditBatch =
        ProtocolJson.decodeBatch(inputText())
            ?: throw IllegalArgumentException("edit batch is required")

    private fun inputText(): String =
        file?.let { fileName ->
            val path = Path.of(fileName)
            val resolved = if (path.isAbsolute) path else model.root.workingDirectory.resolve(path)
            resolved.readText()
        } ?: System.`in`.bufferedReader().readText()
}
