package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class CodeCatalogTest {
    @Test
    fun `creation help uses native receivers and return types`() {
        assertContains(CodeCatalog.text("Project.createLanguage"), "Project.createLanguage(String name, Map options = [:]): Language")
        assertContains(CodeCatalog.text("Language.createGenerator"), "Language.createGenerator(String alias, Map options = [:]): Generator")
        assertContains(CodeCatalog.text("SModule.createModel"), "SModule.createModel(String name, boolean filePerRoot = false): SModel")
    }

    @Test
    fun `access blocks and resolvers are discoverable on project`() {
        val help = CodeCatalog.text("Project")
        assertContains(help, "Project.read")
        assertContains(help, "Project.command")
        assertContains(help, "Project.module")
        assertContains(help, "[read]")
    }

    @Test
    fun `Java parser is discoverable with its command requirement`() {
        val help = CodeCatalog.text("mops.parsing.java")
        assertContains(help, "mops.parsing.java")
        assertContains(help, "Java 8")
        assertContains(CodeCatalog.text("mops.parsing.java.addJavaStatementsFromString"), "[command]")
    }

    @Test
    fun `mops hierarchy exposes parsing and search leaves`() {
        val root = CodeCatalog.text("mops")
        assertContains(root, "mops.parsing.java")
        assertContains(root, "mops.search.eachUsageOf")
        assertContains(root, "mops.search.eachInstanceOf")
        assertContains(CodeCatalog.text("mops.search"), "mops.search.eachUsageOf")
        assertContains(CodeCatalog.text("mops.search.eachInstanceOf"), "[read]")
    }

    @Test
    fun `clean break catalog omits handles and service root`() {
        val help = CodeCatalog.text(null)
        assertFalse("Handle" in help)
        assertFalse("mops.read" in help)
        assertFalse("mops.edit" in help)
        assertFalse("Project.javaParser" in help)
        assertFalse("global.eachUsageOf" in help)
        assertFalse("global.eachInstanceOf" in help)
        assertFailsWith<IllegalArgumentException> { CodeCatalog.text("Project.javaParser") }
        assertFailsWith<IllegalArgumentException> { CodeCatalog.text("JavaSnippetParser.addJavaClassesFromString") }
        assertFailsWith<IllegalArgumentException> { CodeCatalog.text("global.eachUsageOf") }
        assertFailsWith<IllegalArgumentException> { CodeCatalog.text("global.eachInstanceOf") }
    }
}
