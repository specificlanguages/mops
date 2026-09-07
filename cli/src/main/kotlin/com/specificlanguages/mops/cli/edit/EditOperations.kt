package com.specificlanguages.mops.cli.edit

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(name = "edit", description = ["Edit MPS project data."])
class EditOperations : CommandGroup()
