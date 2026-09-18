package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertContains

class CodeCatalogTest {
    @Test
    fun `creation help uses native receivers and return types`() {
        assertContains(CodeCatalog.text("Project.createLanguage"), "Project.createLanguage(String name, Map options = [:]): Language")
        assertContains(CodeCatalog.text("Language.createGenerator"), "Language.createGenerator(String alias, Map options = [:]): Generator")
        assertContains(CodeCatalog.text("SModule.createModel"), "SModule.createModel(String name, boolean filePerRoot = false): SModel")
    }

    @Test
    fun `access blocks are discoverable on project`() {
        val help = CodeCatalog.text("Project")
        assertContains(help, "Project.read")
        assertContains(help, "Project.command")
    }

    @Test
    fun `Java parser is discoverable with its command requirement`() {
        val help = CodeCatalog.text("mops.parsing.java")
        assertContains(help, "mops.parsing.java")
        assertContains(help, "Java 8")
        assertContains(CodeCatalog.text("mops.parsing.java.addJavaStatementsFromString"), "[command]")
    }

    @Test
    fun `mops hierarchy exposes editing parsing lookup and search leaves`() {
        val root = CodeCatalog.text("mops")
        assertContains(root, "mops.editing.build")
        assertContains(root, "mops.parsing.java")
        assertContains(root, "mops.lookup.requireConceptByName")
        assertContains(root, "mops.lookup.requireModule")
        assertContains(root, "mops.lookup.requireModel")
        assertContains(root, "mops.lookup.requireNode")
        assertContains(CodeCatalog.text("mops.lookup.module"), "SModule?")
        assertContains(CodeCatalog.text("mops.lookup.model"), "SModel?")
        assertContains(CodeCatalog.text("mops.lookup.node"), "SNode?")
        assertContains(CodeCatalog.text("mops.lookup.conceptByName"), "SAbstractConcept?")
        assertContains(root, "mops.search.eachUsageOf")
        assertContains(root, "mops.search.eachInstanceOf")
        assertContains(CodeCatalog.text("mops.search"), "mops.search.eachUsageOf")
        assertContains(CodeCatalog.text("mops.search.eachInstanceOf"), "[read]")
        assertContains(CodeCatalog.text("mops.lookup.requireConceptByName"), "[read]")
        assertContains(CodeCatalog.text("mops.editing.build.reloadModulesFromDisk"), "[command]")
    }
}
