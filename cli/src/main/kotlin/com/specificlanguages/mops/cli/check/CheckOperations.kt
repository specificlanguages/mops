package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(name = "check", description = ["Check MPS project data."])
class CheckOperations : CommandGroup()
