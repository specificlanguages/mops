package com.specificlanguages.mops.cli

import picocli.CommandLine.Command

@Command(name = "render", description = ["Render MPS project data."])
class RenderOperations : CommandGroup()
