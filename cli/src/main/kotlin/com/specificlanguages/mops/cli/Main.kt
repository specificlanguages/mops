package com.specificlanguages.mops.cli

import com.specificlanguages.mops.cli.check.CheckOperations
import com.specificlanguages.mops.cli.check.ModelCheckCommand
import com.specificlanguages.mops.cli.code.CodeHelpCommand
import com.specificlanguages.mops.cli.code.CodeOperations
import com.specificlanguages.mops.cli.code.CodeRunCommand
import com.specificlanguages.mops.cli.create.CreateDevkitCommand
import com.specificlanguages.mops.cli.create.CreateGeneratorCommand
import com.specificlanguages.mops.cli.create.CreateLanguageCommand
import com.specificlanguages.mops.cli.create.CreateModelCommand
import com.specificlanguages.mops.cli.create.CreateOperations
import com.specificlanguages.mops.cli.create.CreateSolutionCommand
import com.specificlanguages.mops.cli.daemon.DaemonOperations
import com.specificlanguages.mops.cli.daemon.DaemonPingCommand
import com.specificlanguages.mops.cli.daemon.DaemonStatusCommand
import com.specificlanguages.mops.cli.daemon.DaemonStopCommand
import com.specificlanguages.mops.cli.diagnose.DiagnoseModuleCommand
import com.specificlanguages.mops.cli.diagnose.DiagnoseModulesCommand
import com.specificlanguages.mops.cli.diagnose.DiagnoseOperations
import com.specificlanguages.mops.cli.edit.EditOperations
import com.specificlanguages.mops.cli.edit.ModelEditCommand
import com.specificlanguages.mops.cli.explain.ExplainCommand
import com.specificlanguages.mops.cli.find.FindInstancesCommand
import com.specificlanguages.mops.cli.find.FindNodeByIdCommand
import com.specificlanguages.mops.cli.find.FindOperations
import com.specificlanguages.mops.cli.find.FindRootByNameCommand
import com.specificlanguages.mops.cli.find.FindUsagesCommand
import com.specificlanguages.mops.cli.get.GetOperations
import com.specificlanguages.mops.cli.get.ModelGetNodeCommand
import com.specificlanguages.mops.cli.help.RecursiveHelpCommand
import com.specificlanguages.mops.cli.homes.CreateLauncherCommand
import com.specificlanguages.mops.cli.list.MpsListCommand
import com.specificlanguages.mops.cli.make.MakeModulesCommand
import com.specificlanguages.mops.cli.make.MakeOperations
import com.specificlanguages.mops.cli.make.MakeProjectCommand
import com.specificlanguages.mops.cli.render.ModelRenderNodeCommand
import com.specificlanguages.mops.cli.render.RenderOperations
import java.lang.Exception
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.system.exitProcess
import picocli.CommandLine

fun main(args: Array<String>) {
    exitProcess(newCommandLine().execute(*args))
}

fun newCommandLine(workingDirectory: Path = Path.of("").absolute()): CommandLine {
    val rootCommand = MopsCommand(workingDirectory)
    val root = CommandLine(rootCommand)
    root.commandSpec.parser().allowSubcommandsAsOptionParameters(true)

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
    root.addGroup("create", CreateOperations()) {
        addLeaf("language", CreateLanguageCommand(rootCommand))
        addLeaf("solution", CreateSolutionCommand(rootCommand))
        addLeaf("devkit", CreateDevkitCommand(rootCommand))
        addLeaf("generator", CreateGeneratorCommand(rootCommand))
        addLeaf("model", CreateModelCommand(rootCommand))
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
    val code = CommandLine(CodeOperations())
    code.addLeaf("run", CodeRunCommand(rootCommand))
    code.addLeaf("help", CodeHelpCommand(rootCommand))
    root.addSubcommand("code", code)
    root.addLeaf("list", MpsListCommand(rootCommand))
    root.addLeaf("create-launcher", CreateLauncherCommand(rootCommand))
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
