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

    fun writeWrapper(wrapper: Path, mpsProjectRoot: Path, windows: Boolean): Path {
        wrapper.parent.createDirectories()
        wrapper.writeText(if (windows) windowsWrapper(mpsProjectRoot) else posixWrapper(mpsProjectRoot))
        if (!windows) check(wrapper.toFile().setExecutable(true)) { "Could not make wrapper executable: $wrapper" }
        return wrapper
    }

    internal fun posixWrapper(mpsProjectRoot: Path): String = listOfNotNull(
        "#!/bin/sh\nexec mops",
        mpsHome?.let { "--mps-home=${quoteForShell(it.toString())}" },
        javaHome?.let { "--java-home=${quoteForShell(it.toString())}" },
        "--project-root=${quoteForShell(mpsProjectRoot.toString())}",
        "\"\$@\"\n",
    ).joinToString(" ")

    internal fun windowsWrapper(mpsProjectRoot: Path): String = listOfNotNull(
        "@echo off\r\nmops",
        mpsHome?.let { "--mps-home=${quoteForCmd(it.toString())}" },
        javaHome?.let { "--java-home=${quoteForCmd(it.toString())}" },
        "--project-root=${quoteForCmd(mpsProjectRoot.toString())}",
        "%*\r\n",
    ).joinToString(" ")

    private fun quoteForShell(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun quoteForCmd(value: String): String = "\"${value.replace("%", "%%")}\""
}
