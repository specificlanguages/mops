package com.specificlanguages.mops.cli.get

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(name = "get", description = ["Get MPS project data."])
class GetOperations : CommandGroup()
