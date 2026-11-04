package com.specificlanguages.mops.cli

import com.specificlanguages.mops.cli.explain.ExplainTopics
import com.specificlanguages.mops.cli.help.HelpLayout
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import picocli.CommandLine
import picocli.CommandLine.Help.Ansi

/**
 * Renders the Markdown command reference checked in under `docs/reference/`: one page per top-level command with the
 * `--help` output of the command and of each of its subcommands, one page per `mops explain` topic, and an index page.
 *
 * The help is rendered in-process from the command tree, so generation starts no daemon and needs no MPS.
 */
object CommandReference {
    /** A bundled skill page linked from the index; [path] is relative to the reference directory. */
    data class SkillPage(val path: String, val title: String)

    private const val INDEX = "index.md"
    private const val EXPLAIN_DIR = "explain"
    private const val PROSE_WIDTH = 120

    /** Link from the reference directory, `docs/reference/`, to the repository's `skills/` directory. */
    private const val SKILLS_LINK = "../../skills"

    /** Returns every page of the reference by its path relative to the reference directory. */
    fun pages(skillPages: List<SkillPage>): Map<String, String> {
        val root = newCommandLine()
        val rootHelp = help(root)
        val commands = root.documentedSubcommands()

        val pages = linkedMapOf<String, String>()
        pages[INDEX] = index(rootHelp, commands, skillPages)
        for (command in commands) {
            pages["${command.commandName}.md"] = commandPage(command)
        }
        for (topic in ExplainTopics.topics) {
            pages["$EXPLAIN_DIR/$topic.md"] = explainPage(topic)
        }
        return pages
    }

    /** Encloses [text] in a `text` code fence longer than any backtick run inside it. */
    fun fenced(text: String): String {
        var fence = "```"
        while (text.contains(fence)) fence += "`"
        return "${fence}text\n${text.trimEnd('\n')}\n$fence\n"
    }

    private fun index(rootHelp: String, commands: List<CommandLine>, skillPages: List<SkillPage>) = buildString {
        appendLine("# mops command reference")
        appendLine()
        appendWrapped(generatedNote("the mops command line"))
        appendLine()
        appendLine("## Commands")
        appendLine()
        for (command in commands) {
            appendWrapped("- [mops ${command.commandName}](${command.commandName}.md): ${summary(command)}", "  ")
        }
        appendLine()
        appendLine("## mops")
        appendLine()
        appendWrapped(
            "The global options listed here are accepted by every command. The command pages leave them out of their " +
                "help output.",
        )
        appendLine()
        append(fenced(rootHelp))
        appendLine()
        appendLine("## Notations")
        appendLine()
        for (topic in ExplainTopics.topics) {
            appendWrapped("- [mops explain $topic]($EXPLAIN_DIR/$topic.md): ${ExplainTopics.summaryOf(topic)}", "  ")
        }
        appendLine()
        appendLine("## Skills and task recipes")
        appendLine()
        for (page in skillPages) {
            appendWrapped("- [${page.title}](${page.path})", "  ")
        }
    }

    private fun commandPage(command: CommandLine) = buildString {
        appendLine("# ${qualifiedName(command)}")
        appendLine()
        appendWrapped(
            generatedNote("`${qualifiedName(command)} --help`") + " Global options are listed in the [index]($INDEX).",
        )
        appendCommand(command)
        for (subcommand in command.documentedSubcommands()) {
            appendLine()
            appendLine("## ${qualifiedName(subcommand)}")
            appendCommand(subcommand)
        }
    }

    /** Appends the summary and the help of [command], leaving out the global options that the index lists. */
    private fun StringBuilder.appendCommand(command: CommandLine) {
        appendLine()
        appendWrapped(summary(command))
        appendLine()
        HelpLayout.omitGlobalOptions(command)
        append(fenced(help(command)))
    }

    private fun explainPage(topic: String) = buildString {
        appendLine("# mops explain $topic")
        appendLine()
        appendWrapped(generatedNote("`mops explain $topic`") + " Back to the [index](../$INDEX).")
        appendLine()
        append(fenced(trimLines(ExplainTopics.page(topic))))
    }

    /** Appends [text] wrapped at [PROSE_WIDTH] columns, indenting continuation lines by [indent]. */
    private fun StringBuilder.appendWrapped(text: String, indent: String = "") {
        var line = ""
        for (word in text.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > PROSE_WIDTH) {
                appendLine(line)
                line = indent + word
            } else {
                line = if (line.isEmpty()) word else "$line $word"
            }
        }
        appendLine(line)
    }

    private fun generatedNote(source: String) =
        "Generated from $source by `./gradlew :cli:updateReference`. Do not edit this page."

    /** The line the parent's command list shows: the first header line, else the first description line. */
    private fun summary(command: CommandLine): String {
        val usage = command.commandSpec.usageMessage()
        val line = usage.header().firstOrNull() ?: usage.description().first()
        return line.replace("%n", " ").replace("%%", "%").trim()
    }

    private fun qualifiedName(command: CommandLine) = command.commandSpec.qualifiedName()

    private fun help(command: CommandLine) = trimLines(command.getUsageMessage(Ansi.OFF))

    private fun trimLines(text: String) = text.lines().joinToString("\n") { it.trimEnd() }

    /** Subcommands without their aliases and without the `help` command, which every group has. */
    private fun CommandLine.documentedSubcommands(): List<CommandLine> =
        subcommands.values.distinct().filterNot { it.commandSpec.helpCommand() }

    /**
     * Writes the reference into the directory `args[0]`, linking the skill pages in the `skills/` directory `args[1]`.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val outputDir = Path.of(args[0])
        for ((path, page) in pages(skillPages(Path.of(args[1])))) {
            val file = outputDir.resolve(path)
            file.parent.createDirectories()
            file.writeText(page)
        }
    }

    /** Each skill's `SKILL.md`, then its `references/` pages, titled by their first level-one heading. */
    private fun skillPages(skillsDir: Path): List<SkillPage> =
        skillsDir.listDirectoryEntries().filter { it.isDirectory() }.sortedBy { it.name }.flatMap { skill ->
            val references = skill.resolve("references")
            val referencePages = if (references.isDirectory()) {
                references.listDirectoryEntries("*.md").sortedBy { it.name }
            } else {
                emptyList()
            }
            (listOf(skill.resolve("SKILL.md")) + referencePages).map { page ->
                val title = page.readLines().first { it.startsWith("# ") }.removePrefix("# ")
                SkillPage("$SKILLS_LINK/${skillsDir.relativize(page).joinToString("/")}", title)
            }
        }
}
