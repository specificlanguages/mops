package com.specificlanguages.mops.cli.find

import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind

/**
 * The `#` metadata line a `find` prints when it matched nothing: `# no <subject> in <scope>`. [subject] names what was
 * searched for, such as `usages of foo (ClassConcept)`, so the line tells an empty result apart from a target or scope
 * that was not understood.
 */
internal fun emptyResultLine(
    subject: String,
    scope: SearchScopeJson,
    nodeReferenceFormat: NodeReferenceFormat,
): String = "# no $subject in ${describeScope(scope, nodeReferenceFormat)}"

private fun describeScope(scope: SearchScopeJson, nodeReferenceFormat: NodeReferenceFormat): String {
    val place = when (scope.kind) {
        SearchScopeKind.EDITABLE_PROJECT_SOURCES -> "editable project sources"
        SearchScopeKind.REPOSITORY -> "the repository"
        SearchScopeKind.MODULE -> "module ${scope.name ?: scope.reference}"
        SearchScopeKind.MODEL -> "model ${scope.name ?: scope.reference}"
        SearchScopeKind.SUBTREE ->
            "the subtree of ${scope.name ?: "<unnamed>"} " +
                renderNodeReference(scope.reference.orEmpty(), nodeReferenceFormat)
        SearchScopeKind.LOADED_LANGUAGES -> "loaded languages"
    }
    val models = scope.models ?: return place
    return "$place (searched $models ${if (models == 1) "model" else "models"})"
}

/**
 * Prints [line] where the output mode puts metadata: on stdout among text rows, or on stderr under `--refs-only`, whose
 * stdout must stay a clean stream of node references.
 */
internal fun printMetadata(line: String, refsOnly: Boolean) {
    if (refsOnly) System.err.println(line) else println(line)
}
