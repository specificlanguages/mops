package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.BatchDecodeResult
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import java.nio.file.Path
import kotlin.io.path.readText

@Command(
    name = "edit",
    description = ["Apply a JSON batch of edit operations."],
    footer = ["Operation reference: mops explain edit"],
)
class ModelEditCommand(private val daemonClient: DaemonClient? = null) : CliCommand() {
    @ParentCommand
    lateinit var model: ModelOperations

    @Option(
        names = ["--file"],
        paramLabel = "PATH",
        description = ["Read the edit batch from a JSON file instead of stdin."],
    )
    var file: String? = null

    @Option(
        names = ["--no-constraints"],
        description = ["Apply the batch even if it violates constraints; violations are still reported."],
    )
    var noConstraints: Boolean = false

    override fun run() {
        val batch = readBatch()
        require(batch.operations.isNotEmpty()) { "edit batch must contain at least one operation" }

        val client = daemonClient ?: model.root.ensureDaemon()
        val response = client.modelEdit(batch, force = noConstraints)
        println(ProtocolJson.encodeResponse(response))
    }

    private fun readBatch(): EditBatch =
        when (val result = ProtocolJson.decodeBatchOrError(inputText())) {
            is BatchDecodeResult.Success -> result.batch
            is BatchDecodeResult.Failure -> throw IllegalArgumentException(result.detail)
        }

    private fun inputText(): String =
        file?.let { fileName ->
            val path = Path.of(fileName)
            val resolved = if (path.isAbsolute) path else model.root.workingDirectory.resolve(path)
            resolved.readText()
        } ?: System.`in`.bufferedReader().readText()
}
