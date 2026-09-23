package com.specificlanguages.mops.cli.common

import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.daemoncomms.DaemonPool
import java.nio.file.Path

internal class DaemonClientCommandEnvironment(
    private val daemonClient: DaemonClient,
    override val nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
) : CommandEnvironment {
    override val workingDirectory: Path = Path.of("").toAbsolutePath()

    override fun daemon(projectPathHint: Path): DaemonClient = daemonClient

    override fun daemonPool(): DaemonPool = error("daemon pool is not available in this command environment")

    override fun projectPath(start: Path): Path = error("project path is not available in this command environment")
}
