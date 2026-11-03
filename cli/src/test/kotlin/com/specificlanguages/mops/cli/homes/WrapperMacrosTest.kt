package com.specificlanguages.mops.cli.homes

import org.junit.jupiter.api.io.TempDir
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WrapperMacrosTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `macros used by the project without a value get the Git root and a warning`() {
        val repository = temporary.resolve("repository").also { it.resolve(".git").createDirectories() }
        val project = repository.resolve("mps/project")
        project.writeProjectFiles(
            libraries = """
                <option name="path" value="${'$'}{repo.root}/libs" />
                <option name="path" value="${'$'}{given}/libs" />
                <option name="path" value="${'$'}PROJECT_DIR${'$'}/libs" />
                <option name="path" value="${'$'}{mps_home}/plugins" />
                <option name="path" value="${'$'}{project}/libs" />
                <option name="path" value="${'$'}{platform_lib}/x" />
                <option name="path" value="${'$'}{lib_ext}/x" />
            """,
            modules = """<modulePath path="${'$'}{repo.root}/a.msd" folder="" /><modulePath path="${'$'}{other}/b.msd" folder="" />""",
        )
        val given = temporary.resolve("given").createDirectories()
        val unused = temporary.resolve("unused").createDirectories()
        val warnings = StringWriter()

        val macros = WrapperMacros.complete(project, mapOf("given" to given, "unused" to unused), PrintWriter(warnings, true))

        assertEquals(
            mapOf("given" to given, "other" to repository, "repo.root" to repository, "unused" to unused),
            macros,
        )
        assertEquals(
            listOf(
                "Warning: path macro other used in .mps/modules.xml of $project has no value; using the Git root " +
                    "$repository. To set it, rerun mops wrapper with --macro other=PATH.",
                "Warning: path macro repo.root used in .mps/libraries.xml and .mps/modules.xml of $project has no " +
                    "value; using the Git root $repository. To set it, rerun mops wrapper with --macro repo.root=PATH.",
            ),
            warnings.toString().lines().filter(String::isNotEmpty),
        )
    }

    @Test
    fun `a used macro without a value outside a Git repository fails and names the macro`() {
        val project = temporary.resolve("project")
        project.writeProjectFiles(libraries = """<option name="path" value="${'$'}{repo.root}/libs" />""")

        val failure = assertFailsWith<IllegalStateException> {
            WrapperMacros.complete(project, emptyMap(), PrintWriter(StringWriter()))
        }

        assertEquals(
            "Path macro repo.root used in .mps/libraries.xml of $project has no value, and the project is not in a " +
                "Git repository whose root mops could use instead. Pass --macro repo.root=PATH.",
            failure.message,
        )
    }

    private fun Path.writeProjectFiles(libraries: String? = null, modules: String? = null) {
        val mps = resolve(".mps").createDirectories()
        libraries?.let { mps.resolve("libraries.xml").writeText("<project>$it</project>") }
        modules?.let { mps.resolve("modules.xml").writeText("<project>$it</project>") }
    }
}
