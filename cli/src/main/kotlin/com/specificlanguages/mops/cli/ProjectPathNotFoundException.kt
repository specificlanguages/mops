package com.specificlanguages.mops.cli

import java.nio.file.Path

class MpsHomeRequiredException : RuntimeException(
    "MPS home is required; pass --mps-home <path>, or run mops wrapper in a supported Gradle project " +
        "and then run the wrapper path it prints"
)

class ProjectPathNotFoundException private constructor(message: String) : RuntimeException(message) {
    companion object {
        /** No `.mps` directory in [start] or any of its ancestors. */
        fun upwardFrom(start: Path) = ProjectPathNotFoundException("no .mps directory found from $start upward")

        /** The explicitly given [root] has no `.mps` directory; [projectsBelow] are MPS projects found below it. */
        fun explicitRoot(root: Path, projectsBelow: List<Path>): ProjectPathNotFoundException {
            val hint = if (projectsBelow.isEmpty()) {
                ""
            } else {
                "; MPS projects below it, pass one as --project-root: ${projectsBelow.joinToString(", ")}"
            }
            return ProjectPathNotFoundException("--project-root $root has no .mps directory$hint")
        }
    }
}
