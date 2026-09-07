package com.specificlanguages.mops.daemon

import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.Script
import jetbrains.mps.project.Project
import jetbrains.mps.progress.EmptyProgressMonitor
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SReference
import org.jetbrains.mps.openapi.module.FindUsagesFacade
import org.jetbrains.mps.openapi.module.SearchScope

abstract class CodeModeScript : Script {
    constructor() : super()
    constructor(binding: Binding) : super(binding)

    val project: Project get() = CodeModeProjectProvider.project()

    fun help(): String = CodeCatalog.text(null)
    fun help(subject: Any): String = CodeCatalog.text(subject)

    fun eachUsageOf(node: SNode, scope: SearchScope, body: Closure<*>): Unit {
        requireRead("eachUsageOf")
        FindUsagesFacade.getInstance().findUsages(scope, setOf(node), { reference: SReference -> body.call(reference) }, EmptyProgressMonitor())
    }

    @JvmOverloads
    fun eachInstanceOf(concept: SAbstractConcept, scope: SearchScope, exact: Boolean = false, body: Closure<*>): Unit {
        requireRead("eachInstanceOf")
        FindUsagesFacade.getInstance().findInstances(scope, setOf(concept), exact, { node: SNode -> body.call(node) }, EmptyProgressMonitor())
    }
}

internal fun requireRead(operation: String) {
    check(CodeModeProjectProvider.project().modelAccess.canRead()) { "$operation requires a project.read or project.command access block" }
}

internal fun requireCommand(operation: String) {
    check(CodeModeProjectProvider.project().modelAccess.canWrite()) { "$operation requires a project.command access block" }
}

internal fun requireExtra(operation: String) {
    check(!CodeModeProjectProvider.project().modelAccess.canRead()) { "$operation must run outside an access block" }
}
