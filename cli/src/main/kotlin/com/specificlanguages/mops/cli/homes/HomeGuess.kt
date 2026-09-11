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
    val source: String,
    val mpsHome: Path?,
    val javaHome: Path?,
    val mpsProjectRoot: Path?,
) {
    val usableMps: Boolean get() = mpsHome?.isDirectory() == true
    val usableJava: Boolean get() = javaHome?.let {
        val java = it.resolve("bin/java")
        (java.isRegularFile() && java.isExecutable()) || it.resolve("bin/java.exe").isRegularFile()
    } == true

    fun writeLauncher(windows: Boolean): Path {
        val launcher = buildDir.resolve(if (windows) "mops.cmd" else "mops")
        launcher.parent.createDirectories()
        launcher.writeText(if (windows) windowsLauncher() else posixLauncher())
        if (!windows) check(launcher.toFile().setExecutable(true)) { "Could not make launcher executable: $launcher" }
        return launcher
    }

    internal fun posixLauncher(): String = listOfNotNull(
        "#!/bin/sh\nexec mops",
        mpsHome?.let { "--mps-home=${quoteForShell(it.toString())}" },
        javaHome?.let { "--java-home=${quoteForShell(it.toString())}" },
        mpsProjectRoot?.let { "--project-root=${quoteForShell(it.toString())}" },
        "\"\$@\"\n",
    ).joinToString(" ")

    internal fun windowsLauncher(): String = listOfNotNull(
        "@echo off\r\nmops",
        mpsHome?.let { "--mps-home=${quoteForCmd(it.toString())}" },
        javaHome?.let { "--java-home=${quoteForCmd(it.toString())}" },
        mpsProjectRoot?.let { "--project-root=${quoteForCmd(it.toString())}" },
        "%*\r\n",
    ).joinToString(" ")

    private fun quoteForShell(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun quoteForCmd(value: String): String = "\"${value.replace("%", "%%")}\""
}
