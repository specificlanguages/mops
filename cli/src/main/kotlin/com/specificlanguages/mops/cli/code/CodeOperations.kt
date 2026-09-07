package com.specificlanguages.mops.cli.code

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(name = "code", description = ["Run trusted Groovy against an open MPS project."])
class CodeOperations : CommandGroup()
