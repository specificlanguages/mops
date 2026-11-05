package com.specificlanguages.mops.cli.edit

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.BatchDecodeResult
import com.specificlanguages.mops.protocol.ConstraintMode
import com.specificlanguages.mops.protocol.ConstraintPolicy
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.ProtocolJson
import java.nio.file.Path
import kotlin.io.path.readText
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "model",
    description = ["Apply a JSON batch of edit operations."],
    footer = [
        "",
        "An operation that names a concept resolves it the same way `mops find instances` does: a dropped " +
            "`.structure.` infix is forgiven, and a name that does not resolve fails the batch, changing nothing, " +
            "with the same diagnosis (unknown language, unloaded language, or a \"did you mean\" among a loaded " +
            "language's concepts).",
        "",
        "Operation reference: mops explain edit",
    ],
)
class ModelEditCommand(private val environment: CommandEnvironment) : CliCommand() {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Option(
        names = ["--file"],
        paramLabel = "PATH",
        description = ["Read the edit batch from a JSON file instead of stdin."],
    )
    var file: String? = null

    @Option(
        names = ["--constraints"],
        paramLabel = "MODE",
        description = [
            "What a constraint violation does: advisory (report it and apply the batch anyway) or strict " +
                "(report it and block the batch, saving nothing; default).",
        ],
    )
    var constraintsMode: String? = null

    @Option(
        names = ["--ignore-missing-languages"],
        negatable = true,
        description = [
            "Skip with a warning a concept whose constraints cannot be checked because its language did not " +
                "load. By default such a concept fails the batch.",
        ],
    )
    var ignoreMissingLanguages: Boolean = false

    override fun run() {
        val batch = readBatch()
        require(batch.operations.isNotEmpty()) { "edit batch must contain at least one operation" }

        val client = environment.daemon()
        val response = client.modelEdit(batch, ConstraintPolicy(resolveConstraints(), ignoreMissingLanguages))
        println(renderJson(response))
    }

    private fun resolveConstraints(): ConstraintMode {
        val mode = constraintsMode ?: return ConstraintMode.STRICT
        return ConstraintMode.fromWireName(mode)
            ?: throw IllegalArgumentException(
                "unknown --constraints value '$mode'; expected advisory or strict",
            )
    }

    private fun readBatch(): EditBatch =
        when (val result = ProtocolJson.decodeBatchOrError(inputText())) {
            is BatchDecodeResult.Success -> result.batch
            is BatchDecodeResult.Failure -> throw IllegalArgumentException(result.detail)
        }

    private fun inputText(): String =
        file?.let { fileName ->
            val path = Path.of(fileName)
            val resolved = if (path.isAbsolute) path else environment.workingDirectory.resolve(path)
            resolved.readText()
        } ?: System.`in`.bufferedReader().readText()
}
