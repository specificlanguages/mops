package com.specificlanguages.mops.cli

import picocli.CommandLine.Command

@Command(
    name = "find",
    description = ["Search editable MPS project sources."],
)
class FindOperations : CommandGroup()
