package com.specificlanguages.mops.cli.check

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelCheckResponse
import picocli.CommandLine.IExitCodeGenerator
import picocli.CommandLine.Option

/**
 * Base of the `check` subcommands. Owns the output format and finding limit options, prints the daemon's response, and
 * exits with `1` when the check found at least one error-severity finding (counted over all findings, not only the
 * ones shown). Subclasses only choose what to check.
 */
abstract class CheckCommand(private val environment: CommandEnvironment) : CliCommand(), IExitCodeGenerator {
    @Option(
        names = ["--format"],
        paramLabel = "FORMAT",
        converter = [CheckOutputFormat.Converter::class],
        description = [
            "How to print findings: human (a readable list sorted by severity, then # metadata lines; default) or " +
                "jsonl (one finding object per line, then a summary record).",
        ],
    )
    var format: CheckOutputFormat = CheckOutputFormat.HUMAN

    @Option(
        names = ["--limit"],
        paramLabel = "N",
        description = [
            "Maximum findings to report, most severe first. Defaults to 20 so a broken model does not flood the " +
                "output; 0 means unlimited.",
        ],
    )
    var limit: Int = 20

    private var exitCode: Int = 0

    protected abstract fun check(client: DaemonClient): ModelCheckResponse

    final override fun run() {
        require(limit >= 0) { "limit must not be negative" }
        exitCode = renderCheckResponse(check(environment.daemon()), format, environment.nodeReferenceFormat)
    }

    override fun getExitCode(): Int = exitCode
}
