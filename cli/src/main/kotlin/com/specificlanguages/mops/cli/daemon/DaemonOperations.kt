package com.specificlanguages.mops.cli.daemon

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

/**
 * Picocli command group for daemon lifecycle operations.
 */
@Command(
    name = "daemon",
    description = ["Inspect or control mops daemon processes."],
)
class DaemonOperations : CommandGroup()
