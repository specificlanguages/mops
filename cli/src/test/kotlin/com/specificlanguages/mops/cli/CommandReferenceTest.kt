package com.specificlanguages.mops.cli

import com.specificlanguages.mops.cli.CommandReference.SkillPage
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandReferenceTest {
    private val skillPages = listOf(
        SkillPage("../../skills/mops/SKILL.md", "Working with MPS through mops"),
        SkillPage("../../skills/mops/references/search.md", "Search"),
    )
    private val pages = CommandReference.pages(skillPages)

    @Test
    fun `there is one page per top-level command, without the help command`() {
        val commandPages = pages.keys.filter { !it.contains('/') && it != "index.md" }.sorted()

        assertEquals(
            listOf(
                "check", "code", "create", "daemon", "diagnose", "edit", "examples", "explain", "find", "get", "list",
                "make", "render", "skill", "test", "wrapper",
            ).map { "$it.md" },
            commandPages,
        )
    }

    @Test
    fun `a group page holds the help of the group and of each subcommand under its own heading`() {
        val page = pages.getValue("find.md")

        assertTrue(page.startsWith("# mops find\n"))
        assertContains(page, "Search editable MPS project sources or loaded concept hierarchies.\n")
        assertContains(page, "```text\nUsage: mops find [-h] [COMMAND]\n")
        assertContains(
            page,
            "## mops find instances\n\nFind instances of one MPS concept.\n\n```text\nUsage: mops find instances",
        )
        assertFalse(page.contains("## mops find help"))
    }

    @Test
    fun `command pages leave out the global options, which the index lists once`() {
        assertFalse(pages.getValue("find.md").contains("--mps-home"))
        assertFalse(pages.getValue("find.md").contains("Global options:"))

        val index = pages.getValue("index.md")
        assertEquals(1, Regex("--mps-home").findAll(index).count())
        assertContains(index, "Global options:")
    }

    @Test
    fun `there is one page per explain topic with the page text`() {
        val page = pages.getValue("explain/edit.addChild.md")

        assertTrue(page.startsWith("# mops explain edit.addChild\n"))
        assertContains(page, "```text\nedit.addChild — create a new child node under a containment role.\n")
        assertTrue("explain/scope.md" in pages)
    }

    @Test
    fun `the index links every command page, every explain topic, and the skill pages`() {
        val index = pages.getValue("index.md")

        for (path in pages.keys - "index.md") {
            assertContains(index, "]($path)")
        }
        assertContains(index, "[Working with MPS through mops](../../skills/mops/SKILL.md)")
        assertContains(index, "[Search](../../skills/mops/references/search.md)")
    }

    @Test
    fun `every page says that it is generated`() {
        for ((path, page) in pages) {
            assertContains(page, "./gradlew :cli:updateReference", message = path)
        }
    }

    @Test
    fun `pages have no trailing whitespace and end in one newline`() {
        for ((path, page) in pages) {
            assertFalse(page.lines().any { it != it.trimEnd() }, "$path has trailing whitespace")
            assertTrue(page.endsWith("\n") && !page.endsWith("\n\n"), "$path should end in one newline")
        }
    }

    @Test
    fun `a fence is longer than any backtick run in the text it encloses`() {
        assertEquals("````text\na ``` b\n````\n", CommandReference.fenced("a ``` b"))
        assertEquals("```text\nplain\n```\n", CommandReference.fenced("plain\n"))
    }
}
