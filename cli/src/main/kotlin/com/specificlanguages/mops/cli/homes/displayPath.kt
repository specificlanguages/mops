package com.specificlanguages.mops.cli.homes

import java.nio.file.Path

internal fun displayPath(path: Path, workingDirectory: Path): String {
    val absolute = path.toAbsolutePath().normalize()
    val base = workingDirectory.normalize()
    if (absolute.root != base.root) return absolute.toString()
    return base.relativize(absolute).toString().ifEmpty { "." }
}
