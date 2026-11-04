package com.specificlanguages.mops.cli.help

import picocli.CommandLine
import picocli.CommandLine.Help
import picocli.CommandLine.Help.Ansi
import picocli.CommandLine.Help.Column
import picocli.CommandLine.Help.ColorScheme
import picocli.CommandLine.Help.TextTable
import picocli.CommandLine.IHelpSectionRenderer
import picocli.CommandLine.Model.ArgSpec
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.Model.PositionalParamSpec
import picocli.CommandLine.Model.UsageMessageSpec
import picocli.CommandLine.ScopeType

/**
 * Help layout shared by every mops command: subcommands are listed right after the description so they survive
 * `--help | head`, and the options every command accepts (those declared with [ScopeType.INHERIT] on the root command)
 * are listed once at the end, under `Global options:`, instead of among the command's own options and in the synopsis.
 * Descriptions, footers, option and parameter descriptions, and the command list wrap only at spaces.
 */
object HelpLayout {
    private const val SECTION_KEY_GLOBAL_OPTION_LIST_HEADING = "globalOptionListHeading"
    private const val SECTION_KEY_GLOBAL_OPTION_LIST = "globalOptionList"

    /** Applies the layout to [root] and every subcommand registered so far; only [root]'s help starts with [rootHeader]. */
    fun apply(root: CommandLine, rootHeader: String) {
        val sections = root.helpSectionMap.toMutableMap()
        sections[UsageMessageSpec.SECTION_KEY_PARAMETER_LIST_HEADING] = heading("Parameters:%n") { help ->
            help.commandSpec().positionalParameters().any { !it.hidden() }
        }
        sections[UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING] = heading("Options:%n") { help ->
            help.commandOptions().isNotEmpty()
        }
        sections[UsageMessageSpec.SECTION_KEY_OPTION_LIST] =
            IHelpSectionRenderer { help -> help.optionListExcludingGroups(help.commandOptions()) }
        sections[SECTION_KEY_GLOBAL_OPTION_LIST_HEADING] = heading("Global options:%n") { help ->
            help.globalOptions().isNotEmpty()
        }
        sections[SECTION_KEY_GLOBAL_OPTION_LIST] =
            IHelpSectionRenderer { help -> help.optionListExcludingGroups(help.globalOptions()) }
        root.setHelpSectionMap(sections)
        root.setHelpSectionKeys(sectionKeys(root.helpSectionKeys))
        root.setHelpFactory { spec, colorScheme -> MopsHelp(spec, colorScheme) }
        val rootUsage = root.commandSpec.usageMessage()
        rootUsage.header(rootHeader)
        rootUsage.sectionKeys(
            listOf(UsageMessageSpec.SECTION_KEY_HEADER_HEADING, UsageMessageSpec.SECTION_KEY_HEADER) +
                rootUsage.sectionKeys(),
        )
    }

    /**
     * The line a parent's command list shows for [spec]: the first header line, else the first description line,
     * with format specifiers such as `%n` applied.
     */
    fun summary(spec: CommandSpec): String {
        val usage = spec.usageMessage()
        return String.format(usage.header().firstOrNull() ?: usage.description().firstOrNull() ?: "")
    }

    /** Leaves the global options out of the help of [command] alone; its subcommands keep them. */
    fun omitGlobalOptions(command: CommandLine) {
        val usage = command.commandSpec.usageMessage()
        usage.sectionKeys(
            usage.sectionKeys() - listOf(SECTION_KEY_GLOBAL_OPTION_LIST_HEADING, SECTION_KEY_GLOBAL_OPTION_LIST),
        )
    }

    /**
     * Picocli's default order with the command list moved up after the description, the global options added before
     * the footer, and the header left out (on a subcommand it only serves the parent's command list).
     */
    private fun sectionKeys(defaults: List<String>): List<String> {
        val keys = defaults.toMutableList()
        keys -= listOf(UsageMessageSpec.SECTION_KEY_HEADER_HEADING, UsageMessageSpec.SECTION_KEY_HEADER)
        val commandList =
            listOf(UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING, UsageMessageSpec.SECTION_KEY_COMMAND_LIST)
        keys -= commandList
        keys.addAll(keys.indexOf(UsageMessageSpec.SECTION_KEY_DESCRIPTION) + 1, commandList)
        keys.addAll(
            keys.indexOf(UsageMessageSpec.SECTION_KEY_FOOTER_HEADING),
            listOf(SECTION_KEY_GLOBAL_OPTION_LIST_HEADING, SECTION_KEY_GLOBAL_OPTION_LIST),
        )
        return keys
    }

    /** Leaves the global options out of the synopsis and wraps text with [SpaceWrappingTextTable]. */
    private class MopsHelp(spec: CommandSpec, colorScheme: ColorScheme) : Help(spec, colorScheme) {
        override fun createDetailedSynopsisOptionsText(
            done: Collection<ArgSpec>,
            optionSort: Comparator<OptionSpec>?,
            clusterBooleanOptions: Boolean,
        ): Ansi.Text = createDetailedSynopsisOptionsText(done, commandOptions(), optionSort, clusterBooleanOptions)

        override fun description(vararg params: Any?): String = paragraphs(commandSpec().usageMessage().description())

        override fun header(vararg params: Any?): String = paragraphs(commandSpec().usageMessage().header())

        override fun footer(vararg params: Any?): String = paragraphs(commandSpec().usageMessage().footer())

        override fun createDefaultLayout(
            options: List<OptionSpec>,
            positionals: List<PositionalParamSpec>,
            aColorScheme: ColorScheme,
        ): Layout {
            val longOptionsWidth = calcLongOptionColumnWidth(options, positionals, aColorScheme)
            val columns = TextTable.forDefaultColumns(aColorScheme, longOptionsWidth, width()).columns()
            return Layout(
                aColorScheme,
                SpaceWrappingTextTable(aColorScheme, columns),
                createDefaultOptionRenderer(),
                createDefaultParameterRenderer(),
            )
        }

        /** Picocli's command list: each command with the first line of its header, else of its description. */
        override fun commandList(subcommands: Map<String, Help>): String {
            if (subcommands.isEmpty()) return ""
            val nameWidth = minOf(subcommands.keys.maxOf { it.length }, width() / 2) + 2
            val columns = arrayOf(
                Column(nameWidth, 2, Column.Overflow.SPAN),
                Column(width() - nameWidth, 2, Column.Overflow.WRAP),
            )
            val table = SpaceWrappingTextTable(colorScheme(), columns)
            for (help in subcommands.values) {
                colorScheme().text(summary(help.commandSpec())).splitLines().forEachIndexed { i, line ->
                    table.addRowValues(if (i == 0) help.commandNamesText(", ") else colorScheme().text(""), line)
                }
            }
            return table.toString()
        }

        /** Each of [lines] as its own paragraph, wrapped at the usage width. */
        private fun paragraphs(lines: Array<String>): String {
            val table = SpaceWrappingTextTable(colorScheme(), arrayOf(Column(width(), 0, Column.Overflow.WRAP)))
            table.indentWrappedLines = 0
            for (line in lines) table.addRowValues(String.format(line))
            return table.toString()
        }

        private fun width() = commandSpec().usageMessage().width()
    }

    /**
     * A [TextTable] whose wrapping columns break lines only at spaces. Picocli's own table breaks wherever
     * [java.text.BreakIterator] allows, which includes the point after a `.` that starts a word such as `.mps`.
     */
    private class SpaceWrappingTextTable(private val colorScheme: ColorScheme, columns: Array<Column>) :
        TextTable(colorScheme, columns) {
        override fun putValue(row: Int, col: Int, value: Ansi.Text): Cell {
            val column = columns()[col]
            val text = value.plainString()
            if (column.overflow != Column.Overflow.WRAP || text.isEmpty()) return super.putValue(row, col, value)
            var currentRow = row
            var start = 0
            var indent = ""
            while (true) {
                val end = lineEnd(text, start, column.width - column.indent - indent.length)
                super.putValue(currentRow, col, colorScheme.text(indent).concat(value.substring(start, end)))
                if (end == text.length) return Cell(col, currentRow)
                start = end
                indent = " ".repeat(indentWrappedLines)
                addEmptyRow()
                currentRow++
            }
        }

        /**
         * The end of the longest run of words from [start] that fits into [room] columns, each word counted with the
         * spaces after it, as picocli counts them. A first word longer than [room] is cut at [room].
         */
        private fun lineEnd(text: String, start: Int, room: Int): Int {
            var end = start
            while (end < text.length) {
                var wordEnd = text.indexOf(' ', end).let { if (it < 0) text.length else it }
                while (wordEnd < text.length && text[wordEnd] == ' ') wordEnd++
                if (wordEnd - start > room) break
                end = wordEnd
            }
            return if (end == start) minOf(start + maxOf(room, 1), text.length) else end
        }
    }

    private fun heading(text: String, hasContent: (Help) -> Boolean) =
        IHelpSectionRenderer { help -> if (hasContent(help)) help.createHeading(text) else "" }

    private fun Help.commandOptions(): List<OptionSpec> = visibleOptions().filter { !it.isGlobal() }

    private fun Help.globalOptions(): List<OptionSpec> = visibleOptions().filter { it.isGlobal() }

    private fun Help.visibleOptions(): List<OptionSpec> = commandSpec().options().filter { !it.hidden() }

    /** True on the root command's own declaration and on the copies picocli adds to each subcommand. */
    private fun OptionSpec.isGlobal(): Boolean = scopeType() == ScopeType.INHERIT
}
