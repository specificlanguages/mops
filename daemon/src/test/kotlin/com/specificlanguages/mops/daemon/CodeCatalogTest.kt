package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

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
        val help = CodeCatalog.text("Project.javaParser")
        assertContains(help, "Project.javaParser")
        assertContains(help, "Java 8")
        assertContains(CodeCatalog.text("JavaSnippetParser.addJavaStatementsFromString"), "[command]")
    }

    @Test
    fun `clean break catalog omits handles and service root`() {
        val help = CodeCatalog.text(null)
        assertFalse("Handle" in help)
        assertFalse("mops.read" in help)
        assertFalse("mops.edit" in help)
    }
}
