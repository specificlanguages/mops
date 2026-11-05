package com.specificlanguages.mops.cli

import java.nio.file.Path

class MpsHomeRequiredException : RuntimeException(
    "MPS home is required; pass --mps-home <path>, or run mops wrapper in a supported Gradle project " +
        "and then run the wrapper path it prints"
)

class ProjectPathNotFoundException(startingPath: Path) :
    RuntimeException("no .mps directory found from $startingPath upward")

/** An MPS or Java home given on the command line does not exist. */
class RuntimeHomeNotFoundException(description: String, home: Path, wrapperStart: Path) : RuntimeException(
    "$description does not exist: $home. If a mops wrapper passed it, the wrapper is out of date: " +
        "run mops wrapper $wrapperStart to write it again."
)
