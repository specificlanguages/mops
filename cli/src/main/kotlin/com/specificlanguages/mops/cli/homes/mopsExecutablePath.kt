package com.specificlanguages.mops.cli.homes

import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal fun mopsExecutablePath(
    codeLocation: Path = Path.of(WrapperCommand::class.java.protectionDomain.codeSource.location.toURI()),
    windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
): Path {
    val location = codeLocation.toAbsolutePath().normalize()
    val executableName = if (windows) "mops.bat" else "mops"
    return generateSequence(location) { it.parent }
        .flatMap { sequenceOf(it.resolve("bin/$executableName"), it.resolve("install/mops/bin/$executableName")) }
        .firstOrNull { it.isRegularFile() }
        ?: error("Could not locate the mops executable from $location; run wrapper from an installed mops distribution.")
}
