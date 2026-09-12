package com.specificlanguages.mops.cli

import java.nio.file.Path

class MpsHomeRequiredException : RuntimeException(
    "MPS home is required; pass --mps-home <path>, or run mops wrapper in a supported Gradle project " +
        "and then run the wrapper path it prints"
)

class ProjectPathNotFoundException(startingPath: Path) :
    RuntimeException("no .mps directory found from $startingPath upward")
