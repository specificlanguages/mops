package com.specificlanguages.mops.cli

import java.nio.file.Path

/**
 * An MPS or Java home given on the command line does not exist. [regenerateCommand] is the `mops wrapper` command line
 * that writes the wrapper again.
 */
class RuntimeHomeNotFoundException(homeName: String, home: Path, regenerateCommand: String) : RuntimeException(
    "$homeName does not exist: $home. If a mops wrapper passed it, the wrapper is out of date: " +
        "run $regenerateCommand to write it again."
)
