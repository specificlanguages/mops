package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.daemoncomms.DaemonLauncher
import com.specificlanguages.mops.protocol.DaemonContext
import com.specificlanguages.mops.protocol.DaemonRecord
import java.nio.file.Path

class RecordingLauncher(private val projectStateDirResolver: (DaemonContext) -> Path = { Path.of("unused") }) : DaemonLauncher {
    var context: DaemonContext? = null
    var projectStateDir: Path? = null

    val existingDaemons: MutableMap<DaemonContext, DaemonClient> = mutableMapOf()

    override fun startDaemon(context: DaemonContext): DaemonClient {
        this.context = context
        this.projectStateDir = projectStateDirResolver(context)
        return FakeDaemonClient
    }

    override fun connectToExistingDaemon(record: DaemonRecord): DaemonClient {
        return existingDaemons[record.context] ?: FakeDaemonClient
    }
}
