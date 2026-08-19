package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.daemoncomms.DaemonPool
import java.nio.file.Path

/** Runtime facilities shared by commands, independent of their position in the command tree. */
interface CommandEnvironment {
    val workingDirectory: Path

    fun daemon(projectPathHint: Path = workingDirectory): DaemonClient

    fun daemonPool(): DaemonPool

    fun projectPath(start: Path = workingDirectory): Path
}
