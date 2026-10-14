package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import com.specificlanguages.mops.protocol.MakeOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives `mops.runtime` against a fixture-project copy. The copy carries no compiled output, so the language is
 * unusable until a make runs inside the same project; both states are exercised in one open project.
 */
class CodeModeRuntimeTest {
    @Test
    fun `runtime loads classes from a made module and names the failure case otherwise`() {
        SharedMpsEnvironment.withOpenProjectCopy("expression-language") { project, _ ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())
            val executor = CodeModeExecutor(access, project, SharedMpsEnvironment.platform)
            fun run(program: String): String? = executor.execute(CodeRunRequest("", program, "runtime.groovy")).output

            assertEquals("ok", run("""
                $FAILS
                fails(IllegalStateException, 'mops.runtime.loadClass requires a project.read') { mops.runtime.loadClass('exprs', 'exprs.structure.StructureAspectDescriptor') }
                fails(IllegalStateException, 'mops.runtime.requireClass requires a project.read') { mops.runtime.requireClass('exprs', 'exprs.structure.StructureAspectDescriptor') }
                project.read {
                    assert help(mops.runtime).contains('mops.runtime.loadClass')
                    assert help(mops.runtime).contains('mops.runtime.requireClass')
                    fails(IllegalArgumentException, 'module not found: no.such.module') { mops.runtime.loadClass('no.such.module', 'Any') }
                    fails(IllegalArgumentException, 'module not found: no.such.module') { mops.runtime.requireClass('no.such.module', 'Any') }
                    fails(IllegalArgumentException, 'has no class loader') { mops.runtime.loadClass('jetbrains.mps.devkit.general-purpose', 'Any') }
                    def behavior = mops.runtime.requireClass('jetbrains.mps.baseLanguage', 'jetbrains.mps.baseLanguage.behavior.ClassConcept__BehaviorDescriptor')
                    assert behavior.name == 'jetbrains.mps.baseLanguage.behavior.ClassConcept__BehaviorDescriptor'
                    assert mops.runtime.loadClass('jetbrains.mps.baseLanguage', 'jetbrains.mps.baseLanguage.behavior.NoSuchClass') == null
                    fails(IllegalStateException, 'mops make module exprs') { mops.runtime.loadClass('exprs', 'exprs.structure.StructureAspectDescriptor') }
                    fails(IllegalStateException, 'not compiled') { mops.runtime.requireClass('exprs', 'exprs.structure.StructureAspectDescriptor') }
                }
                return 'ok'
            """.trimIndent()))

            val made = access.extra { makeModules(listOf("exprs")) }
            assertEquals(MakeOutcome.SUCCESS, made.outcome, "unexpected errors: ${made.messages}")

            assertEquals("ok", run("""
                $FAILS
                project.read {
                    def module = mops.lookup.requireModule('exprs')
                    def descriptor = mops.runtime.requireClass('exprs', 'exprs.structure.StructureAspectDescriptor')
                    assert descriptor.name == 'exprs.structure.StructureAspectDescriptor'
                    assert descriptor.classLoader.loadOwnClass('exprs.structure.StructureAspectDescriptor').is(descriptor)
                    assert mops.runtime.loadClass(module.moduleReference.toString(), 'exprs.structure.StructureAspectDescriptor').is(descriptor)
                    assert mops.runtime.loadClass('exprs', 'exprs.structure.NoSuchClass') == null
                    fails(IllegalArgumentException, 'class not found in module exprs: exprs.structure.NoSuchClass') {
                        mops.runtime.requireClass('exprs', 'exprs.structure.NoSuchClass')
                    }
                    def constraints = mops.runtime.requireClass('exprs', 'exprs.constraints.ConstraintsAspectDescriptor')
                    assert constraints.getDeclaredConstructor().newInstance() != null
                }
                return 'ok'
            """.trimIndent()))
        }
    }

    private companion object {
        val FAILS = """
            def fails = { Class type, String fragment, Closure action ->
                try { action(); assert false: 'Expected ' + type.name }
                catch (Exception failure) {
                    assert type.isInstance(failure): failure
                    assert failure.message.contains(fragment): failure.message
                }
            }
        """.trimIndent()
    }
}
