package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.TestRunReport
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

object MopsTestingExtensions {
    /** Builds and runs a project, module, model, or root test case in an isolated worker. Individual test methods are unsupported. Options: build (default true), timeout in seconds (default 900; 0 disables). Returns a saved report, including failures and interruptions. Access: extra. */
    @CodeModeExtension(MpsAccessLevel.EXTRA)
    @JvmStatic
    @JvmOverloads
    fun run(testing: MopsTesting, selection: Any = testing.mops.project, options: Map<String, Any?> = emptyMap()): TestRunReport {
        requireOwner(testing.mops); requireExtra("mops.testing.run")
        require(options.keys.all { it in setOf("build", "timeout") }) { "unknown testing option" }
        val build = options["build"]?.let { require(it is Boolean) { "build must be a boolean" }; it } ?: true
        val seconds = options["timeout"]?.let {
            require(it is Number && it.toDouble() == it.toLong().toDouble()) { "timeout must be an integer number of seconds" }
            it.toLong()
        } ?: 900L
        require(seconds in 0..(Long.MAX_VALUE / 1000 - System.currentTimeMillis())) { "invalid timeout" }
        val deadline = if (seconds == 0L) 0 else System.currentTimeMillis() + seconds * 1000
        val mops = testing.mops
        val target = mops.access.read {
            val persistence = PersistenceFacade.getInstance()
            when (selection) {
                mops.project -> emptyList()
                is SModule -> {
                    require(selection.moduleReference.resolve(mops.project.repository) === selection) { "module belongs to another repository" }
                    listOf(persistence.asString(selection.moduleReference))
                }
                is SModel -> {
                    require(selection.reference.resolve(mops.project.repository) === selection) { "model belongs to another repository" }
                    listOf(persistence.asString(selection.reference))
                }
                is SNode -> {
                    require(selection.reference.resolve(mops.project.repository) === selection) { "node is detached or belongs to another repository" }
                    listOf(persistence.asString(selection.reference))
                }
                else -> throw IllegalArgumentException("selection must be the open project, a module, model, or test node")
            }
        }
        return requireNotNull(mops.testingRunner) { "test runner unavailable" }.run(target, build, deadline, null)
    }
}
