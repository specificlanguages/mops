package com.specificlanguages.mops.cli.examples

import com.specificlanguages.mops.cli.common.CliCommand
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(name = "examples", description = ["Read bundled task recipes without starting MPS. Omit the topic to find where to start."])
class ExamplesCommand : CliCommand() {
    @Parameters(index = "0", arity = "0..1", paramLabel = "TOPIC", description = ["Topic such as editing, editing.java, or all."])
    var topic: String? = null

    override fun run() {
        print(topic?.let(ExampleTopics::page) ?: ExampleTopics.index())
    }
}
