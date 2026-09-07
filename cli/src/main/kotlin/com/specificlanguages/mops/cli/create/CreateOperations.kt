package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(name = "create", description = ["Create a project module or model."])
class CreateOperations : CommandGroup()
