package com.specificlanguages.mops.daemon

import groovy.lang.Binding
import groovy.lang.Script
import jetbrains.mps.project.MPSProject

abstract class CodeModeScript : Script {
    constructor() : super()
    constructor(binding: Binding) : super(binding)

    val project: MPSProject get() = CodeModeProjectProvider.project()
    val mops: Mops get() = CodeModeProjectProvider.mops()

    fun help(): String = CodeCatalog.text(null)
    fun help(subject: Any): String = CodeCatalog.text(subject)

}

internal fun requireOwner(mops: Mops) {
    check(CodeModeProjectProvider.mops() === mops) { "mops belongs to a different Code Mode execution" }
}

internal fun requireRead(mops: Mops, operation: String) {
    requireOwner(mops)
    check(mops.project.modelAccess.canRead()) { "$operation requires a project.read or project.command access block" }
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
