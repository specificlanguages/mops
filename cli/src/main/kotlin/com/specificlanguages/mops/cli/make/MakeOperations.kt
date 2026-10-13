package com.specificlanguages.mops.cli.make

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

/**
 * Picocli command group for running the MPS make (generation and compilation) on the project.
 */
@Command(
    name = "make",
    header = ["Generate and compile modules or the whole project."],
    description = ["Run the MPS make (generation and compilation) on modules or the whole project."],
)
class MakeOperations : CommandGroup()
