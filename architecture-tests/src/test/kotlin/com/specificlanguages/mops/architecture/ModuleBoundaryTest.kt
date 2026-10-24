package com.specificlanguages.mops.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

class ModuleBoundaryTest {
    @Test
    fun `protocol does not use MPS`() {
        Konsist.scopeFromModule("protocol").files.assertFalse { it.importsAnyOf(MPS_PACKAGES) }
    }

    @Test
    fun `protocol does not use other mops modules`() {
        Konsist.scopeFromModule("protocol").files.assertFalse {
            it.importsAnyOf(MOPS_PACKAGES - PROTOCOL)
        }
    }

    @Test
    fun `daemon-core does not use MPS`() {
        Konsist.scopeFromModule("daemon-core").files.assertFalse { it.importsAnyOf(MPS_PACKAGES) }
    }

    @Test
    fun `cli does not use MPS or daemon classes`() {
        Konsist.scopeFromModule("cli").files.assertFalse { it.importsAnyOf(MPS_PACKAGES + DAEMON) }
    }

    @Test
    fun `daemon does not use cli classes`() {
        Konsist.scopeFromModule("daemon").files.assertFalse { it.importsAnyOf(listOf(CLI, DAEMONCOMMS)) }
    }

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

    private fun KoFileDeclaration.importsAnyOf(packages: Collection<String>): Boolean =
        imports.any { import -> packages.any { isInPackage(import.name, it) } }

    /** Whether [name] is [pkg] itself or a name inside it, including in its subpackages. */
    private fun isInPackage(name: String, pkg: String): Boolean = name == pkg || name.startsWith("$pkg.")

    private companion object {
        const val MOPS = "com.specificlanguages.mops"
        const val CLI = "$MOPS.cli"
        const val DAEMON = "$MOPS.daemon"
        const val DAEMONCOMMS = "$MOPS.daemoncomms"
        const val LAUNCHER = "$MOPS.launcher"
        const val PROTOCOL = "$MOPS.protocol"

        val MOPS_PACKAGES = listOf(CLI, DAEMON, DAEMONCOMMS, LAUNCHER, PROTOCOL)

        val MPS_PACKAGES = listOf("jetbrains.mps", "org.jetbrains.mps")

        val SHARED_CLI_PACKAGES = setOf("$CLI.common", "$CLI.output")

        /** `WrapperCommand` reads the working directory and project root from the root `MopsCommand`. */
        val KNOWN_ROOT_CLI_USERS = setOf("$CLI.homes.WrapperCommand.kt")
    }
}
