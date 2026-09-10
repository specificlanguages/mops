package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.protocol.*
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class ProjectSettingsSemanticsTest {
    @Test
    fun `external membership removal is loaded before creation and survives saving`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, path ->
            val handler = DomainRequestHandler(path, JetBrainsMpsAccess(project, DaemonLogger()))
            val descriptor = path.resolve(".mps/modules.xml")
            descriptor.writeText(descriptor.readText().lines()
                .filterNot { it.contains("solutions/json.sandbox/") }.joinToString("\n"))

            assertIs<ModuleCreationResponse>(handler.handleDomainRequest(CreateSolutionRequest("", "example.created")))

            project.modelAccess.computeReadAction {
                assertFalse(project.projectModules.any { it.moduleName == "json.sandbox" })
                assertTrue(project.projectModules.any { it.moduleName == "example.created" })
            }
            assertFalse(descriptor.readText().contains("solutions/json.sandbox/"))
            assertContains(descriptor.readText(), "solutions/example.created/example.created.msd")
        }
    }

    @Test
    fun `external membership addition is visible to the next request`() {
        var original = ""
        SharedMpsEnvironment.withOpenProjectCopy(prepare = { path ->
            val descriptor = path.resolve(".mps/modules.xml")
            original = descriptor.readText()
            descriptor.writeText(original.lines().filterNot { it.contains("solutions/json.sandbox/") }.joinToString("\n"))
        }) { project, path ->
            val handler = DomainRequestHandler(path, JetBrainsMpsAccess(project, DaemonLogger()))
            path.resolve(".mps/modules.xml").writeText(original)

            val response = handler.handleDomainRequest(MpsListRequest("", depth = 1))
            assertIs<MpsListResponse>(response, response.toString())
            assertTrue(response.root.children.orEmpty().any { it.name == "json.sandbox" })
        }
    }

    @Test
    fun `read requests leave clean project settings unchanged`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, path ->
            val handler = DomainRequestHandler(path, JetBrainsMpsAccess(project, DaemonLogger()))
            val descriptor = path.resolve(".mps/modules.xml")
            val original = descriptor.readText()
            val modified = Files.getLastModifiedTime(descriptor)

            repeat(2) {
                assertIs<MpsListResponse>(handler.handleDomainRequest(MpsListRequest("", depth = 0)))
                assertEquals(original, descriptor.readText())
                assertEquals(modified, Files.getLastModifiedTime(descriptor))
            }
        }
    }

    @Test
    fun `clean save preserves an external edit made during the request`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, path ->
            val descriptor = path.resolve(".mps/modules.xml")
            val external = descriptor.readText().lines()
                .filterNot { it.contains("solutions/json.sandbox/") }.joinToString("\n")
            val access = JetBrainsMpsAccess(project, DaemonLogger())
            val concurrentEdit = object : MpsAccess by access {
                override fun <T> read(block: MpsRead.() -> T): T = access.read(block).also {
                    descriptor.writeText(external)
                }
            }

            assertIs<MpsListResponse>(DomainRequestHandler(path, concurrentEdit)
                .handleDomainRequest(MpsListRequest("", depth = 0)))
            assertEquals(external, descriptor.readText())
        }
    }
}
