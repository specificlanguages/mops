package com.specificlanguages.mops.cli

import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A daemon that starts with a built project language and an unbuilt project solution that the language depends on.
 * MPS registers a runtime for the unbuilt solution and loads the language, so the diagnosis reports neither module as
 * a problem and names no fix.
 */
@ResourceLock("system-streams")
class UnbuiltDependencyIntegrationTest {
    @Test
    fun `an unbuilt dependency solution does not stop a built language from loading`() {
        val project = daemons.project("mps-json", copy = "unbuilt-dependency")
        fun run(vararg args: String) = project.run(*args).also { assertEquals(0, it.exitCode, it.output) }

        run("create", "solution", SOLUTION, "--usage-preset", "java")
        run("create", "model", "$SOLUTION.main", "--module", SOLUTION)
        val program = project.root.resolve("add-dependency.groovy")
        program.writeText("""
            import jetbrains.mps.project.structure.modules.Dependency
            import org.jetbrains.mps.openapi.persistence.PersistenceFacade
            project.command {
              def solution = mops.lookup.requireModule('$SOLUTION')
              def solutionDescriptor = solution.moduleDescriptor
              solutionDescriptor.dependencies.add(new Dependency(
                PersistenceFacade.instance.createModuleReference('$JDK'), false))
              solution.setModuleDescriptor(solutionDescriptor)
              solution.save()
              mops.parsing.java.addJavaClassesFromString(mops.lookup.requireModel('$SOLUTION.main'),
                'public class Util { public static int one() { return 1; } }')
              def language = mops.lookup.requireModule('$LANGUAGE')
              def languageDescriptor = language.moduleDescriptor
              languageDescriptor.dependencies.add(new Dependency(solution.moduleReference, false))
              language.setModuleDescriptor(languageDescriptor)
              language.save()
              'ok'
            }
        """.trimIndent())
        run("code", "run", program.pathString)
        run("make", "module", LANGUAGE)

        // With the daemon stopped, removing the solution's compiled classes leaves it unbuilt.
        run("daemon", "stop")
        val classes = project.root.resolve("solutions/$SOLUTION/classes_gen")
        assertTrue(classes.isDirectory(), "the make should have compiled $SOLUTION into $classes")
        classes.toFile().deleteRecursively()

        assertEquals("$SOLUTION\tsolution\tpresent=true\tloaded=true", run("diagnose", "module", SOLUTION).stdout.trim())
        assertEquals("$LANGUAGE\tlanguage\tpresent=true\tloaded=true", run("diagnose", "module", LANGUAGE).stdout.trim())
        run("find", "instances", CONCEPT)
    }

    companion object {
        @JvmField
        @RegisterExtension
        val daemons = SharedDaemons()

        private const val LANGUAGE = "com.specificlanguages.json"
        private const val CONCEPT = "$LANGUAGE.structure.JsonFile"
        private const val SOLUTION = "example.util"
        private const val JDK = "6354ebe7-c22a-4a0f-ac54-50b52ab9b065(JDK)"
    }
}
