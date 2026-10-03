package com.specificlanguages.mops.cli.skill

import com.specificlanguages.mops.cli.common.CliCommand
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(name = "path", description = ["Print the absolute path to a bundled SKILL.md. Load it directly or symlink its directory into your agent's skills directory."])
class SkillPathCommand : CliCommand() {
    @Parameters(index = "0", arity = "0..1", paramLabel = "NAME", description = ["Skill name: mops (default), mops-daemon (alias: daemon)."])
    var name: String = "mops"

    override fun run() {
        println(BundledSkill.path(name))
    }
}
