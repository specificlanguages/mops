package com.specificlanguages.mops.cli.diagnose

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

/**
 * Picocli command group for diagnosing the daemon's view of an MPS project.
 */
@Command(
    name = "diagnose",
    description = ["Diagnose how the daemon loaded the MPS project."],
)
class DiagnoseOperations : CommandGroup()
