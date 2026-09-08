package com.specificlanguages.mops.cli.homes

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

internal data class HomeGuess(
    val projectDir: Path,
    val source: String,
    val mpsHome: Path?,
    val javaHome: Path?,
    val diagnostics: List<String>,
) {
    val usableMps: Boolean get() = mpsHome?.isDirectory() == true
    val usableJava: Boolean get() = javaHome?.let {
        val java = it.resolve("bin/java")
        (java.isRegularFile() && java.isExecutable()) || it.resolve("bin/java.exe").isRegularFile()
    } == true

    fun commandLine(): String = listOfNotNull(
        "mops",
        mpsHome?.takeIf { usableMps }?.let { "--mps-home=${quote(it.toString())}" },
        javaHome?.takeIf { usableJava }?.let { "--java-home=${quote(it.toString())}" },
    ).joinToString(" ")

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
