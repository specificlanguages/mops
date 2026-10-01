package com.specificlanguages.mops.cli.skill

import com.specificlanguages.mops.cli.common.CommandGroup
import picocli.CommandLine.Command

@Command(name = "skill", description = ["Locate bundled agent skills."])
class SkillOperations : CommandGroup()
