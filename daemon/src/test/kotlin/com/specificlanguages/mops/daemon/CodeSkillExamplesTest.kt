package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.io.path.readText
import kotlin.test.*

class CodeSkillExamplesTest {
    @TestFactory
    fun `skill examples execute verbatim against MPS`(): List<DynamicTest> {
        val skill = checkNotNull(javaClass.getResourceAsStream("/mops/SKILL.md"))
            .bufferedReader().use { it.readText() }
        val blocks = Regex("""(?m)^```groovy\r?\n([\s\S]*?)^```\r?$""").findAll(skill).toList()
        assertTrue(blocks.isNotEmpty(), "The skill must contain executable examples")
        val examples = blocks.associate { block ->
            val heading = skill.substring(0, block.range.first).lineSequence().last { it.startsWith("### ") }.removePrefix("### ")
            heading to block.groupValues[1]
        }
        assertEquals(blocks.size, examples.size, "Each example needs a distinct heading")
        assertEquals(setOf("List roots", "Find classes", "Add a class"), examples.keys, "Every example needs behavioral assertions")
        return examples.map { (heading, source) ->
            DynamicTest.dynamicTest(heading) {
                SharedMpsEnvironment.withOpenProjectCopy("base-language-sandbox") { project, projectPath ->
                    val executor = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform)
                    fun run(program: String) = checkNotNull(executor.execute(CodeRunRequest("", program, "skill-example.groovy")).output)
                    val result = JsonSlurper().parseText(run(source))
                    val nodes = when (heading) {
                        "List roots" -> (result as List<*>).also { roots ->
                            assertEquals(listOf("Calculator"), roots.map { (it as Map<*, *>)["name"] })
                            assertEquals("jetbrains.mps.baseLanguage.structure.ClassConcept", (roots.single() as Map<*, *>)["concept"])
                        }.map { it as Map<*, *> }
                        "Find classes" -> (result as List<*>).also { found ->
                            assertEquals(setOf("Calculator", "Inner"), found.map { (it as Map<*, *>)["name"] }.toSet())
                        }.map { it as Map<*, *> }
                        else -> listOf(result as Map<*, *>).also {
                            assertEquals("SkillExample", result["name"])
                            val persisted = projectPath.resolve("solutions/baselanguage.sandbox/models/baselanguage.sandbox.mps").readText()
                            assertContains(persisted, "SkillExample", message = "The command must save its new root")
                        }
                    }
                    for (node in nodes) {
                        val reference = node["node"] as String
                        val actualName = run("project.read { mops.lookup.requireNode(${JsonOutput.toJson(reference)}).properties['name'] }")
                        assertEquals(node["name"], actualName, "Returned references must resolve to the documented nodes")
                    }
                }
            }
        }
    }
}
