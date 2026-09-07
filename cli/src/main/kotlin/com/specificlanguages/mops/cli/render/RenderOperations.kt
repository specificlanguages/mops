package com.specificlanguages.mops.cli.render

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(name = "render", description = ["Render MPS project data."])
class RenderOperations : CommandGroup()
