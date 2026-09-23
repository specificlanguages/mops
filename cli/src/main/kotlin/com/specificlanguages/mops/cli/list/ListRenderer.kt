package com.specificlanguages.mops.cli.list

import com.specificlanguages.mops.cli.output.displayConcept
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.cli.output.renderNodeReference
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListSummaryGroupJson
import com.specificlanguages.mops.protocol.MpsListSummaryJson

internal class ListRenderer(
    val fullConcept: Boolean,
    val nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
) {
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
        listOf(
            name ?: "<unnamed>",
            concept?.let { displayConcept(it, fullConcept) }.orEmpty(),
            reference?.let { renderNodeReference(it, nodeReferenceFormat) }.orEmpty(),
        ) +
                listOfNotNull(error)
}
