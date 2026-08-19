package com.specificlanguages.mops.cli

import picocli.CommandLine.Command

/**
 * Picocli command group for running the MPS make (generation and compilation) on the project.
 */
@Command(
    name = "make",
    description = ["Run the MPS make (generation and compilation) on modules or the whole project."],
)
class MakeOperations : CommandGroup()
