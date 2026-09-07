package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(
    name = "find",
    description = ["Search editable MPS project sources."],
)
class FindOperations : CommandGroup()
