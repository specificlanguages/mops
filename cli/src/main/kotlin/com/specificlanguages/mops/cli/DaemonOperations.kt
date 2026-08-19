package com.specificlanguages.mops.cli

import picocli.CommandLine.Command

/**
 * Picocli command group for daemon lifecycle operations.
 */
@Command(
    name = "daemon",
    description = ["Inspect or control mops daemon processes."],
)
class DaemonOperations : CommandGroup()
