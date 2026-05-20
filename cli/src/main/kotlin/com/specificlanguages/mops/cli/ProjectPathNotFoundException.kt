package com.specificlanguages.mops.cli

import java.nio.file.Path

class MpsHomeRequiredException : RuntimeException("MPS home is required; pass --mps-home <path>")

class ProjectPathNotFoundException(startingPath: Path) :
    RuntimeException("no .mps directory found from $startingPath upward")
