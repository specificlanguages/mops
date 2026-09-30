package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.*

class CodeCatalogTest {
    @Test
    fun `containment navigation is discoverable with read access and traversal semantics`() {
        val descendants = CodeCatalog.text("SNode.descendants")
        assertContains(descendants, "[read]")
        assertContains(descendants, "depth-first pre-order")
        assertContains(descendants, "excluding this node")
        val ancestors = CodeCatalog.text("SNode.ancestors")
        assertContains(ancestors, "[read]")
        assertContains(ancestors, "parent to root")
        assertContains(ancestors, "excluding this node")
    }

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

    @Test
    fun `root is a compact index and aliases select the same operation`() {
        val root = CodeCatalog.document(null)
        assertTrue(root.entries.isEmpty())
        assertContains(root.index, "mops.search")
        assertEquals(CodeCatalog.document("Project.createSolution"), CodeCatalog.document("project.createSolution"))
        assertEquals(CodeCatalog.document("createSolution"), CodeCatalog.document("project.createSolution"))
    }

    @Test
    fun `structured help records options parameters and result members`() {
        val entry = CodeCatalog.document("createSolution").entries.single()
        assertEquals(listOf("name", "options"), entry.parameters.map { it.name })
        assertEquals(listOf("descriptor", "usagePreset"), entry.options.map { it.name })
        assertEquals("[: ]".replace(" ", ""), entry.parameters.last().default)
        assertContains(entry.options.last().values, "JAVA_TESTS")
        assertTrue(entry.examples.single().startsWith("return project.command"))
        assertEquals(listOf("nodes", "unresolved"), CodeCatalog.document("JavaParsingResult").entries.map { it.path.substringAfterLast('.') })
        assertEquals("List<SNode>", CodeCatalog.document("JavaParsingResult.nodes").entries.single().returnType)
        assertContains(CodeCatalog.json("createSolution"), "\"options\"")
    }

    @Test
    fun `native type string lookup includes inherited extensions and structured members`() {
        val solution = CodeCatalog.document("Solution")
        assertContains(solution.entries.map { it.path }, "SModule.createModel")
        assertNotNull(solution.nativeType)
        val node = CodeCatalog.document("SNode")
        assertTrue(node.nativeType!!.properties.single { it.name == "children" }.shadowedBy.contains("SNode.children"))
    }

    @Test
    fun `all registered extension operations have help and matching JVM arity and return type`() {
        CodeHelpApi.methods.groupBy(CodeHelpApi::path).forEach { (path, overloads) ->
            val entry = CodeCatalog.document(path).entries.single { it.path == path }
            assertTrue(entry.examples.isNotEmpty(), path)
            val full = overloads.maxBy { it.parameterCount }
            assertEquals(full.parameterCount - 1, entry.parameters.size, path)
            assertContains(entry.access, full.getAnnotation(CodeModeExtension::class.java).access.name.lowercase())
            full.parameterTypes.drop(1).zip(entry.parameters).forEach { (actual, parameter) ->
                val documented = parameter.type.substringBefore('<').removeSuffix("?")
                val expectedType = when (actual.simpleName) { "Object" -> "Object"; else -> actual.simpleName }
                assertEquals(expectedType, documented, "$path.${parameter.name}")
            }
            val requiredCount = entry.parameters.count { it.default == null }
            val expectedArities = (requiredCount..entry.parameters.size).toList()
            assertEquals(expectedArities, overloads.map { it.parameterCount - 1 }.distinct().sorted(), path)
            val actualReturn = full.returnType.simpleName
            val expected = when (actualReturn) { "Object" -> "T"; "void" -> "void"; else -> actualReturn }
            assertEquals(expected, entry.returnType, path)
        }
    }

    @Test
    fun `JSON faithfully represents the same document used by text`() {
        listOf(null, "mops", "createSolution", "SNode", "JavaParsingResult").forEach { subject ->
            assertEquals(CodeCatalog.document(subject), com.specificlanguages.mops.protocol.ProtocolJson.decodeCodeHelp(CodeCatalog.json(subject)))
        }
    }

    @Test
    fun `examples compile as complete Groovy scripts`() {
        groovy.lang.GroovyClassLoader(javaClass.classLoader).use { loader ->
            CodeCatalog.document("mops").entries.plus(CodeCatalog.document("Project").entries).plus(CodeCatalog.document("SNode").entries).plus(CodeCatalog.document("Language").entries).forEach { entry ->
                entry.examples.forEachIndexed { index, example -> loader.parseClass(example, "Example${entry.path.replace('.', '_')}${index}.groovy") }
            }
        }
    }

    @Test
    fun `unknown names suggest nearby paths`() {
        assertContains(assertFailsWith<IllegalArgumentException> { CodeCatalog.text("createSoluton") }.message!!, "createSolution")
    }
}
