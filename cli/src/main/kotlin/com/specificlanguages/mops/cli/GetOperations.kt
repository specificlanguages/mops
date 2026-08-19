package com.specificlanguages.mops.cli

import picocli.CommandLine.Command

@Command(name = "get", description = ["Get MPS project data."])
class GetOperations : CommandGroup()
