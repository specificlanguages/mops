package com.specificlanguages.mops.cli

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

/**
 * Prints reference pages for mops Notations from embedded topic files.
 *
 * Pure and offline: it never starts a daemon, resolves a project root, or requires --mps-home. With no argument it lists
 * the topics; with a dot-path it prints that page verbatim. An unknown path exits non-zero with sibling suggestions.
 */
@Command(
    name = "explain",
    description = ["Explain mops Notations (the textual formats mops exchanges with agents)."],
)
class ExplainCommand : CliCommand() {
    @Parameters(
        index = "0",
        arity = "0..1",
        paramLabel = "PATH",
        description = ["Topic dot-path, e.g. edit or edit.copyNode. Omit to list topics."],
    )
    var path: String? = null

    @Option(
        names = ["--schema"],
        description = ["Print the generated JSON Schema for the edit-batch Notation (only with the edit topic)."],
    )
    var schema: Boolean = false

    override fun run() {
        val requested = path
        if (schema) {
            require(requested == "edit") { "--schema applies only to the edit topic; run `mops explain edit --schema`" }
            print(ExplainTopics.editSchema())
        } else if (requested == null) {
            print(ExplainTopics.index())
        } else {
            print(ExplainTopics.page(requested))
        }
    }
}
