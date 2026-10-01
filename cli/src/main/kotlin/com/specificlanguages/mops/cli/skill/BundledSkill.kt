package com.specificlanguages.mops.cli.skill

import java.nio.file.Path
import kotlin.io.path.isRegularFile

object BundledSkill {
    fun path(
        name: String = "mops-code",
        codeLocation: Path = Path.of(BundledSkill::class.java.protectionDomain.codeSource.location.toURI()),
    ): Path {
        val skillName = when (name) {
            "mops-code", "code" -> "mops-code"
            "mops-daemon", "daemon" -> "mops-daemon"
            else -> throw IllegalArgumentException("Unknown skill '$name'; available: mops-code (alias: code), mops-daemon (alias: daemon)")
        }
        val location = codeLocation.toAbsolutePath().normalize()
        return generateSequence(location) { it.parent }
            .map { it.resolve("skills/$skillName/SKILL.md") }
            .firstOrNull { it.isRegularFile() }
            ?: error("Could not locate the bundled $skillName skill from $location")
    }
}
