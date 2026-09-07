package com.specificlanguages.mops.cli.create

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.*
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(name = "language", description = ["Create a language project module."])
class CreateLanguageCommand(environment: CommandEnvironment) : CreateModuleCommand(environment) {
    constructor(client: DaemonClient) : this(DaemonClientCommandEnvironment(client))
    @Parameters(index = "0", paramLabel = "MODULE_NAME") lateinit var moduleName: String
    @Option(names = ["--descriptor"], paramLabel = "FILE") var descriptor: String? = null
    @Option(names = ["--with-generator"]) var withGenerator = false
    override fun run() = render(environment.daemon().createLanguage(moduleName, descriptor, withGenerator, dryRun))
}
