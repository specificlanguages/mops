package com.specificlanguages.mops.cli

import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class MpsHomeRequiredException : RuntimeException(
    "MPS home is required; pass --mps-home <path>, or run mops wrapper in a supported Gradle project " +
        "and then run the wrapper path it prints"
)

class ProjectPathNotFoundException private constructor(message: String) : RuntimeException(message) {
    companion object {
        private const val MAX_CANDIDATE_DEPTH = 3
        private const val MAX_CANDIDATES = 3
        private const val MAX_VISITED_DIRECTORIES = 10_000

        /** No `.mps` directory in [start] or any of its ancestors. */
        fun upwardFrom(start: Path) = ProjectPathNotFoundException("no .mps directory found from $start upward")

        /** The explicitly given [root] has no `.mps` directory; names projects found a few levels below it. */
        fun explicitRoot(root: Path): ProjectPathNotFoundException {
            val candidates = projectsBelow(root)
            val hint = if (candidates.isEmpty()) "" else "; MPS projects below it: ${candidates.joinToString(", ")}"
            return ProjectPathNotFoundException("--project-root $root has no .mps directory$hint")
        }

        /**
         * Breadth-first search for directories holding `.mps`, at most [MAX_CANDIDATE_DEPTH] levels below [root].
         * Skips hidden directories and symbolic links, and stops after [MAX_CANDIDATES] projects or
         * [MAX_VISITED_DIRECTORIES] directories.
         */
        private fun projectsBelow(root: Path): List<Path> {
            val found = mutableListOf<Path>()
            var level = listOf(root)
            var visited = 0
            repeat(MAX_CANDIDATE_DEPTH) {
                val next = mutableListOf<Path>()
                for (parent in level) {
                    for (child in subdirectories(parent)) {
                        if (found.size == MAX_CANDIDATES || ++visited > MAX_VISITED_DIRECTORIES) return found
                        if (child.resolve(".mps").isDirectory()) found.add(child)
                        next.add(child)
                    }
                }
                level = next
            }
            return found
        }

        private fun subdirectories(directory: Path): List<Path> =
            runCatching { directory.listDirectoryEntries() }.getOrDefault(emptyList())
                .filter { !it.name.startsWith(".") && it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
                .sorted()
    }
}
