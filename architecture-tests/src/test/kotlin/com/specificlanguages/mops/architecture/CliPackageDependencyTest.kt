package com.specificlanguages.mops.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

class CliPackageDependencyTest {
    /**
     * Each CLI command package (`cli.find`, `cli.get`, ...) builds only on the shared CLI packages, the daemon client,
     * and the other modules. The root `cli` package wires the commands together, so a command package that imports it
     * or another command package creates a dependency between commands.
     */
    @Test
    fun `cli command packages do not use each other or the root cli package`() {
        Konsist.scopeFromProduction(moduleName = "cli").files
            .filter { it.packagee?.name?.startsWith("$CLI.") == true }
            .filterNot { it.packagee?.name in SHARED_CLI_PACKAGES }
            .filterNot { "${it.packagee?.name}.${it.nameWithExtension}" in KNOWN_ROOT_CLI_USERS }
            .assertFalse { file ->
                val ownPackage = file.packagee!!.name
                file.imports.any { import ->
                    val importedPackage = import.name.substringBeforeLast('.')
                    isInPackage(importedPackage, CLI) &&
                        importedPackage !in SHARED_CLI_PACKAGES &&
                        !isInPackage(importedPackage, ownPackage)
                }
            }
    }

    /** Whether [name] is [pkg] itself or a name inside it, including in its subpackages. */
    private fun isInPackage(name: String, pkg: String): Boolean = name == pkg || name.startsWith("$pkg.")

    private companion object {
        const val CLI = "com.specificlanguages.mops.cli"

        val SHARED_CLI_PACKAGES = setOf("$CLI.common", "$CLI.output")

        /** `WrapperCommand` reads the working directory and project root from the root `MopsCommand`. */
        val KNOWN_ROOT_CLI_USERS = setOf("$CLI.homes.WrapperCommand.kt")
    }
}
