package com.specificlanguages.mops.cli

import picocli.CommandLine.Command

@Command(name = "code", description = ["Run trusted Groovy against an open MPS project."])
class CodeOperations : CommandGroup()
