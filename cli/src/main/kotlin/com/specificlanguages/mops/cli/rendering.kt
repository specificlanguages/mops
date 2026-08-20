package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonResponse
import com.specificlanguages.mops.protocol.ModelCheckFindingJson
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListSummaryGroupJson
import com.specificlanguages.mops.protocol.MpsListSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodeParentJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeUsageJson
import com.specificlanguages.mops.protocol.ProtocolJson

data class RenderingOptions(val fullConcept: Boolean = false)

internal fun renderJson(response: DaemonResponse): String = ProtocolJson.encodeResponse(response)

internal fun renderJson(node: MpsNodeJson): String = ProtocolJson.encodeNode(node)

internal fun renderJson(entry: MpsListEntryJson): String = ProtocolJson.encodeListEntry(entry)

internal fun renderJson(finding: ModelCheckFindingJson): String = ProtocolJson.encodeFinding(finding)

internal fun renderText(node: MpsNodeSummaryJson, fullConcept: Boolean): String =
    (
        listOf(
            node.type,
            node.name ?: "<unnamed>",
            displayConcept(node.concept, fullConcept),
            node.reference,
        ) + parentColumns(node.parent, fullConcept)
    ).joinToString("\t")

internal fun renderText(usage: MpsNodeUsageJson, fullConcept: Boolean): String {
    val owner = usage.owner
    return (
        listOf(
            "usage",
            usage.role,
            owner.name ?: "<unnamed>",
            displayConcept(owner.concept, fullConcept),
            owner.reference,
        ) + parentColumns(owner.parent, fullConcept)
    ).joinToString("\t")
}

internal class ListRenderer(val fullConcept: Boolean) {
    internal fun renderText(entry: MpsListEntryJson, indent: Int) {
        println("${"  ".repeat(indent)}${entry.columns().joinToString("\t")}")

        val summary = entry.summary
        if (summary != null) {
            summary.groups.forEach { group ->
                println("${"  ".repeat(indent + 1)}${summaryColumns(summary, group).joinToString("\t")}")
            }
            return
        }

        entry.children.orEmpty().forEach { child -> renderText(child, indent + 1) }
        entry.childTotal?.let { total ->
            val shown = entry.children.orEmpty().size
            println("${"  ".repeat(indent + 1)}${listOf("truncated", shown, total).joinToString("\t")}")
        }
    }

    private fun summaryColumns(
        summary: MpsListSummaryJson,
        group: MpsListSummaryGroupJson,
    ): List<String> {
        // A "concept" grouping keys each group by a concept qualified name; the other axes key by role, model, or kind.
        val key = if (summary.by == "concept") displayConcept(group.key, fullConcept) else group.key
        val concepts = group.concepts?.takeIf { it.isNotEmpty() }
            ?.let { listOf(it.joinToString(", ") { concept -> displayConcept(concept, fullConcept) }) }
            ?: emptyList()
        return listOf(summary.by, key, group.count.toString()) + concepts
    }

    private fun MpsListEntryJson.columns(): List<String> =
        when (type) {
            "project" -> listOf("project", name.orEmpty())
            "repository" -> listOf("repository", name.orEmpty())
            "module" -> listOf(moduleKind ?: "other", name.orEmpty(), reference.orEmpty())
            "model" -> listOf("model", name.orEmpty(), reference.orEmpty())
            "root" -> nodeColumns("root")
            "node" -> listOf("node", role.orEmpty()) + nodeColumnsWithoutType()
            else -> listOf(type, name.orEmpty(), reference.orEmpty())
        }

    private fun MpsListEntryJson.nodeColumns(typeColumn: String): List<String> =
        listOf(typeColumn) + nodeColumnsWithoutType()

    private fun MpsListEntryJson.nodeColumnsWithoutType(): List<String> =
        listOf(name ?: "<unnamed>", concept?.let { displayConcept(it, fullConcept) }.orEmpty(), reference.orEmpty()) +
                listOfNotNull(error)
}


/**
 * The concept name as shown in a text column: the bare short name (the last dotted segment) by default, or the full
 * qualified name when [full] is set. The shortening is purely lexical here; short names are safe as the default because
 * the daemon's concept-name resolver accepts them on the way back in. JSON output keeps the qualified name
 * unconditionally and never passes through here.
 */
internal fun displayConcept(concept: String, full: Boolean): String =
    if (full) concept else concept.substringAfterLast('.')

/**
 * Trailing tab columns describing a result node's immediate parent: its name (or `<unnamed>`), concept, and serialized
 * node reference. The concept is shown as a short name unless [fullConcept] is set. Empty when the node is a Root Node,
 * so root results keep their existing shorter rows.
 */
internal fun parentColumns(parent: MpsNodeParentJson?, fullConcept: Boolean): List<String> =
    if (parent == null) {
        emptyList()
    } else {
        listOf("parent", parent.name ?: "<unnamed>", displayConcept(parent.concept, fullConcept), parent.reference)
    }
