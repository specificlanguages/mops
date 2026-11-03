package com.specificlanguages.mops.cli.homes

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeText

internal data class HomeGuess(
    val projectDir: Path,
    val buildDir: Path,
    val source: String?,
    val mpsHome: Path?,
    val javaHome: Path?,
    val mpsProjectRoots: List<Path>,
) {
    val hasKnownHomes: Boolean get() = mpsHome != null && javaHome != null
    val usableMps: Boolean get() = mpsHome?.isDirectory() == true
    val usableJava: Boolean get() = javaHome?.let {
        val java = it.resolve("bin/java")
        (java.isRegularFile() && java.isExecutable()) || it.resolve("bin/java.exe").isRegularFile()
    } == true

    fun writeWrapper(
        wrapper: Path,
        mpsProjectRoot: Path,
        mopsExecutable: Path,
        windows: Boolean,
        macros: Map<String, Path> = emptyMap(),
    ): Path {
        wrapper.parent.createDirectories()
        wrapper.writeText(
            if (windows) {
                windowsWrapper(mpsProjectRoot, mopsExecutable, macros)
            } else {
                posixWrapper(mpsProjectRoot, mopsExecutable, macros)
            },
        )
        if (!windows) check(wrapper.toFile().setExecutable(true)) { "Could not make wrapper executable: $wrapper" }
        return wrapper
    }

    internal fun posixWrapper(mpsProjectRoot: Path, mopsExecutable: Path, macros: Map<String, Path> = emptyMap()): String =
        wrapperArguments(mpsProjectRoot, macros, ::quoteForShell)
            .joinToString(" ", prefix = "#!/bin/sh\nexec ${quoteForShell(mopsExecutable.toAbsolutePath().toString())} ", postfix = " \"\$@\"\n")

    internal fun windowsWrapper(mpsProjectRoot: Path, mopsExecutable: Path, macros: Map<String, Path> = emptyMap()): String =
        wrapperArguments(mpsProjectRoot, macros, ::quoteForCmd)
            .joinToString(" ", prefix = "@echo off\r\n${quoteForCmd(mopsExecutable.toAbsolutePath().toString())} ", postfix = " %*\r\n")

    private fun wrapperArguments(mpsProjectRoot: Path, macros: Map<String, Path>, quote: (String) -> String): List<String> =
        listOfNotNull(
            mpsHome?.let { "--mps-home=${quote(it.toString())}" },
            javaHome?.let { "--java-home=${quote(it.toString())}" },
            "--project-root=${quote(mpsProjectRoot.toString())}",
        ) + macros.map { (name, path) -> "--macro=${quote("$name=$path")}" }

    private fun quoteForShell(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun quoteForCmd(value: String): String = "\"${value.replace("%", "%%")}\""
}
