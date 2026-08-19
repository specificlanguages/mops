package com.specificlanguages.mops.cli

import picocli.CommandLine.Command

@Command(name = "edit", description = ["Edit MPS project data."])
class EditOperations : CommandGroup()
