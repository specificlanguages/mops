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
    val rootCommand = MopsCommand(workingDirectory)
    val root = CommandLine(rootCommand)

    root.addGroup("find", FindOperations()) {
        addLeaf("instances", FindInstancesCommand(rootCommand))
        addLeaf("usages", FindUsagesCommand(rootCommand))
        addLeaf("root-by-name", FindRootByNameCommand(rootCommand))
        addLeaf("node-by-id", FindNodeByIdCommand(rootCommand))
    }
    root.addGroup("get", GetOperations()) {
        addLeaf("node", ModelGetNodeCommand(rootCommand))
    }
    root.addGroup("render", RenderOperations()) {
        addLeaf("node", ModelRenderNodeCommand(rootCommand))
    }
    root.addGroup("edit", EditOperations()) {
        addLeaf("model", ModelEditCommand(rootCommand))
    }
    root.addGroup("check", CheckOperations()) {
        addLeaf("model", ModelCheckCommand(rootCommand))
    }
    root.addGroup("make", MakeOperations()) {
        addLeaf("module", MakeModulesCommand(rootCommand))
        addLeaf("project", MakeProjectCommand(rootCommand))
    }
    root.addGroup("diagnose", DiagnoseOperations()) {
        addLeaf("module", DiagnoseModuleCommand(rootCommand))
        addLeaf("project", DiagnoseModulesCommand(rootCommand))
    }
    root.addGroup("daemon", DaemonOperations()) {
        addLeaf("ping", DaemonPingCommand(rootCommand))
        addLeaf("status", DaemonStatusCommand(rootCommand))
        addLeaf("stop", DaemonStopCommand(rootCommand))
    }
    root.addLeaf("list", MpsListCommand(rootCommand))
    root.addLeaf("explain", ExplainCommand())
    root.addLeaf("help", RecursiveHelpCommand())

    return root.setExecutionExceptionHandler(PrintErrorAndExit)
}

private fun CommandLine.addGroup(name: String, command: Any, configure: CommandLine.() -> Unit) {
    val group = CommandLine(command)
    group.configure()
    group.addLeaf("help", RecursiveHelpCommand())
    addSubcommand(name, group)
}

private fun CommandLine.addLeaf(name: String, command: Any) {
    addSubcommand(name, command)
}

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
