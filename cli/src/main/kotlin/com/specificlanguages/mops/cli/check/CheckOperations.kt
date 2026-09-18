package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(name = "check", description = ["Run MPS checks."])
class CheckOperations : CommandGroup()
