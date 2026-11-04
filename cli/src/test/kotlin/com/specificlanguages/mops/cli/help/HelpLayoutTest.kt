package com.specificlanguages.mops.cli.help

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import picocli.CommandLine
import picocli.CommandLine.Help.Ansi
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.OptionSpec

class HelpLayoutTest {
    @Test
    fun `a word that starts with a dot stays on one line in descriptions, option descriptions, and footers`() {
        for (padding in 1..90) {
            val text = "${"x".repeat(padding)} the .mps directory and the .devkit file stay whole"
            val spec = CommandSpec.create().name("demo")
            spec.usageMessage().description(text).footer(text)
            spec.addOption(OptionSpec.builder("--option").description(text).build())
            val help = helpOf(spec)

            for (word in listOf(".mps", ".devkit")) {
                assertEquals(3, Regex(Regex.escape(word) + "\\b").findAll(help).count(), "padding $padding:\n$help")
            }
        }
    }

    @Test
    fun `lines still wrap at the usage width and continuation lines keep their indent`() {
        val spec = CommandSpec.create().name("demo")
        spec.addOption(OptionSpec.builder("--option").description("word ".repeat(30).trim()).build())
        val help = helpOf(spec)

        val lines = help.lines().filter { it.contains("word") }
        assertEquals(listOf(true), lines.map { it.length <= 80 }.distinct(), help)
        assertContains(lines[0], "--option   word")
        assertEquals(lines[0].indexOf("word") + 2, lines[1].indexOf("word"), help)
    }

    @Test
    fun `a word longer than the line is cut`() {
        val spec = CommandSpec.create().name("demo")
        spec.usageMessage().description("a ${"y".repeat(100)} b")
        val help = helpOf(spec)

        assertContains(help, "a\n${"y".repeat(80)}\n${"y".repeat(20)} b\n")
    }

    @Test
    fun `a line after a cut word does not start with a space`() {
        val spec = CommandSpec.create().name("demo")
        spec.usageMessage().description("${"u".repeat(80)} end")
        val help = helpOf(spec)

        assertContains(help, "${"u".repeat(80)}\nend\n")
    }

    private fun helpOf(spec: CommandSpec): String {
        val commandLine = CommandLine(spec)
        HelpLayout.apply(commandLine)
        return commandLine.getUsageMessage(Ansi.OFF)
    }
}
