package com.specificlanguages.mops.cli

import picocli.CommandLine.Command

@Command(name = "check", description = ["Check MPS project data."])
class CheckOperations : CommandGroup()
