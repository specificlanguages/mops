package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.daemoncomms.DefaultDaemonPool
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.system.exitProcess
import picocli.CommandLine
import java.lang.Exception

fun main(args: Array<String>) {
    exitProcess(newCommandLine().execute(*args))
}

fun newCommandLine(workingDirectory: Path = Path.of("").absolute()): CommandLine {
    return newCommandLine(MopsCommand(workingDirectory))
}

private fun newCommandLine(mopsCommand: MopsCommand): CommandLine =
    CommandLine(mopsCommand).setExecutionExceptionHandler(PrintErrorAndExit)

object PrintErrorAndExit : CommandLine.IExecutionExceptionHandler {
    override fun handleExecutionException(
        exception: Exception,
        commandLine: CommandLine,
        fullParseResult: CommandLine.ParseResult?
    ): Int {
        commandLine.err.println(exception.message ?: exception::class.java.name)
        return 1
    }
}