package com.specificlanguages.mops.cli.help

import picocli.CommandLine
import picocli.CommandLine.Help
import picocli.CommandLine.Help.Ansi
import picocli.CommandLine.Help.ColorScheme
import picocli.CommandLine.IHelpSectionRenderer
import picocli.CommandLine.Model.ArgSpec
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.Model.UsageMessageSpec
import picocli.CommandLine.ScopeType

/**
 * Help layout shared by every mops command: subcommands are listed right after the description so they survive
 * `--help | head`, and the options every command accepts (those declared with [ScopeType.INHERIT] on the root command)
 * are listed once at the end, under `Global options:`, instead of among the command's own options and in the synopsis.
 */
object HelpLayout {
    private const val SECTION_KEY_GLOBAL_OPTION_LIST_HEADING = "globalOptionListHeading"
    private const val SECTION_KEY_GLOBAL_OPTION_LIST = "globalOptionList"

    private val sectionKeys = listOf(
        UsageMessageSpec.SECTION_KEY_SYNOPSIS_HEADING,
        UsageMessageSpec.SECTION_KEY_SYNOPSIS,
        UsageMessageSpec.SECTION_KEY_DESCRIPTION_HEADING,
        UsageMessageSpec.SECTION_KEY_DESCRIPTION,
        UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING,
        UsageMessageSpec.SECTION_KEY_COMMAND_LIST,
        UsageMessageSpec.SECTION_KEY_PARAMETER_LIST_HEADING,
        UsageMessageSpec.SECTION_KEY_PARAMETER_LIST,
        UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING,
        UsageMessageSpec.SECTION_KEY_OPTION_LIST,
        UsageMessageSpec.SECTION_KEY_EXIT_CODE_LIST_HEADING,
        UsageMessageSpec.SECTION_KEY_EXIT_CODE_LIST,
        SECTION_KEY_GLOBAL_OPTION_LIST_HEADING,
        SECTION_KEY_GLOBAL_OPTION_LIST,
        UsageMessageSpec.SECTION_KEY_FOOTER_HEADING,
        UsageMessageSpec.SECTION_KEY_FOOTER,
    )

    /** Applies the layout to [root] and every subcommand registered so far. */
    fun apply(root: CommandLine) {
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
        root.setHelpSectionKeys(sectionKeys)
        root.setHelpFactory { spec, colorScheme -> SynopsisWithoutGlobalOptions(spec, colorScheme) }
    }

    private class SynopsisWithoutGlobalOptions(spec: CommandSpec, colorScheme: ColorScheme) : Help(spec, colorScheme) {
        override fun createDetailedSynopsisOptionsText(
            done: Collection<ArgSpec>,
            optionSort: Comparator<OptionSpec>?,
            clusterBooleanOptions: Boolean,
        ): Ansi.Text = createDetailedSynopsisOptionsText(done, commandOptions(), optionSort, clusterBooleanOptions)
    }

    private fun heading(text: String, hasContent: (Help) -> Boolean) =
        IHelpSectionRenderer { help -> if (hasContent(help)) help.createHeading(text) else "" }

    private fun Help.commandOptions(): List<OptionSpec> = visibleOptions().filter { !it.isGlobal() }

    private fun Help.globalOptions(): List<OptionSpec> = visibleOptions().filter { it.isGlobal() }

    private fun Help.visibleOptions(): List<OptionSpec> = commandSpec().options().filter { !it.hidden() }

    /** True on the root command's own declaration and on the copies picocli adds to each subcommand. */
    private fun OptionSpec.isGlobal(): Boolean = scopeType() == ScopeType.INHERIT
}
