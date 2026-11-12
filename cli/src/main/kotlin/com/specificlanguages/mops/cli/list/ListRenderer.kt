package com.specificlanguages.mops.cli.list

import com.specificlanguages.mops.cli.output.displayConcept
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.cli.output.TextColumn
import com.specificlanguages.mops.cli.output.nodeReferenceColumn
import com.specificlanguages.mops.cli.output.truncationLine
import com.specificlanguages.mops.cli.output.textRow
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListSummaryGroupJson
import com.specificlanguages.mops.protocol.MpsListSummaryJson

internal class ListRenderer(
    val fullConcept: Boolean,
    val nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED,
) {
    internal fun renderText(entry: MpsListEntryJson, indent: Int) {
        println("${"  ".repeat(indent)}${textRow(entry.columns())}")

        val summary = entry.summary
        if (summary != null) {
            summary.groups.forEach { group ->
                println("${"  ".repeat(indent + 1)}${textRow(summaryColumns(summary, group))}")
            }
            return
        }

        entry.children.orEmpty().forEach { child -> renderText(child, indent + 1) }
        entry.childTotal?.let { total ->
            val shown = entry.children.orEmpty().size
            println("${"  ".repeat(indent + 1)}${truncationLine(shown, total, unit = "children")}")
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

    private fun MpsListEntryJson.columns(): List<TextColumn> =
        when (type) {
            "project" -> plainColumns("project", name.orEmpty())
            "repository" -> plainColumns("repository", name.orEmpty())
            "module" -> plainColumns(moduleKind ?: "other", name.orEmpty(), reference.orEmpty())
            "model" -> plainColumns("model", name.orEmpty(), reference.orEmpty())
            "root" -> plainColumns("root") + nodeColumnsWithoutType()
            "node" -> plainColumns("node", role.orEmpty()) + nodeColumnsWithoutType()
            else -> plainColumns(type, name.orEmpty(), reference.orEmpty())
        }

    private fun MpsListEntryJson.nodeColumnsWithoutType(): List<TextColumn> =
        plainColumns(name ?: "<unnamed>", concept?.let { displayConcept(it, fullConcept) }.orEmpty()) +
            (reference?.let { nodeReferenceColumn(it, nodeReferenceFormat) } ?: TextColumn("")) +
            listOfNotNull(error).map { TextColumn(it) }

    private fun plainColumns(vararg texts: String): List<TextColumn> = texts.map { TextColumn(it) }
}
