package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@Serializable
@ConsistentCopyVisibility
data class DaemonContext internal constructor(
    @Serializable(with = PathAsStringSerializer::class) val realProjectPath: Path,
    @Serializable(with = PathAsStringSerializer::class) val realMpsHome: Path,
    @Serializable(with = PathAsStringSerializer::class) val realJavaHome: Path,
) {
    companion object {
        fun fromLivePaths(projectPath: Path, mpsHome: Path, javaHome: Path) =
            DaemonContext(
                toRealPathChecked("project path", projectPath),
                toRealPathChecked("MPS home", mpsHome),
                toRealPathChecked("Java home", javaHome))

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
