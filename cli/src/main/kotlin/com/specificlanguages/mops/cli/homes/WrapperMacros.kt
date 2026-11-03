package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.common.mpsBuiltInPathMacros
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** Path macros a project wrapper passes to mops: the given ones, plus a guess for each one the project uses without a value. */
internal object WrapperMacros {
    private val projectFiles = listOf(".mps/libraries.xml", ".mps/modules.xml")

    // MPS expands a path macro only at the start of a path.
    private val macroUse = Regex("""="\$\{([^}"]+)}""")

    /**
     * Returns [given] plus a value for every path macro used in the project files of [projectRoot] that [given] lacks.
     * Such a macro gets the root of the Git repository holding the project, with a warning on [warnings]; without a
     * Git repository, this fails naming the macro.
     */
    fun complete(projectRoot: Path, given: Map<String, Path>, warnings: PrintWriter): Map<String, Path> {
        val missing = usedMacros(projectRoot).filterKeys { it !in given }
        if (missing.isEmpty()) return given.toSortedMap()
        val gitRoot = gitRoot(projectRoot)
        fun use(name: String, files: List<String>) = "path macro $name used in ${files.joinToString(" and ")} of $projectRoot"
        val guessed = missing.mapValues { (name, files) ->
            checkNotNull(gitRoot) {
                "${use(name, files).replaceFirstChar(Char::uppercase)} has no value, and the project is not in a " +
                    "Git repository whose root mops could use instead. Pass --macro $name=PATH."
            }
        }
        missing.forEach { (name, files) ->
            warnings.println(
                "Warning: ${use(name, files)} has no value; using the Git root $gitRoot. " +
                    "To set it, rerun mops wrapper with --macro $name=PATH.",
            )
        }
        return (given + guessed).toSortedMap()
    }

    /** Maps each path macro the project files use, apart from MPS built-ins, to the files that use it. */
    private fun usedMacros(projectRoot: Path): Map<String, List<String>> =
        projectFiles
            .filter { projectRoot.resolve(it).isRegularFile() }
            .flatMap { file ->
                macroUse.findAll(projectRoot.resolve(file).readText()).map { it.groupValues[1] to file }.distinct()
            }
            .filter { (name, _) -> name !in mpsBuiltInPathMacros }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()

    private fun gitRoot(projectRoot: Path): Path? =
        generateSequence(projectRoot) { it.parent }.firstOrNull { it.resolve(".git").exists() }
}
