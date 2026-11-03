package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * What a daemon was started with. A running daemon serves a command only when the command asks for the same context.
 *
 * [macros] are the path macros the daemon defines in MPS before it opens the project, by name, with real paths as
 * values.
 */
@Serializable
@ConsistentCopyVisibility
data class DaemonContext internal constructor(
    @Serializable(with = PathAsStringSerializer::class) val realProjectPath: Path,
    @Serializable(with = PathAsStringSerializer::class) val realMpsHome: Path,
    @Serializable(with = PathAsStringSerializer::class) val realJavaHome: Path,
    val macros: Map<String, String> = emptyMap(),
) {
    companion object {
        fun fromLivePaths(projectPath: Path, mpsHome: Path, javaHome: Path, macros: Map<String, Path> = emptyMap()) =
            DaemonContext(
                toRealPathChecked("project path", projectPath),
                toRealPathChecked("MPS home", mpsHome),
                toRealPathChecked("Java home", javaHome),
                macros.toSortedMap().mapValues { (name, path) -> toRealPathChecked("path macro $name", path).toString() },
            )

        private fun toRealPathChecked(description: String, path: Path): Path =
            try {
                path.toRealPath()
            } catch (e: NoSuchFileException) {
                throw IllegalArgumentException("File does not exist when resolving $description $path: ${e.message}")
            } catch (e: IOException) {
                throw IllegalArgumentException("Error resolving $description $path: ${e.message}", e)
            }
    }
}
