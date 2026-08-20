package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.EditNotation

/**
 * Resolves `mops explain` topic paths to their embedded page text and builds the topic index.
 *
 * Pages are plain-text classpath resources under `explain/`. The topic tree is two levels deep: top-level Notation
 * topics plus the `edit.<op>` operation pages, whose set is derived from [EditNotation] so it cannot drift from the wire
 * format.
 */
object ExplainTopics {
    private val topLevelTopics =
        listOf("code", "edit", "inline-subtree", "target", "position", "node-ref", "name-pattern", "scope")

    private val operationTopics: List<String> = EditNotation.operationNames.map { "edit.$it" }

    private val allTopics: List<String> = topLevelTopics + operationTopics

    /** Renders the topic index: each top-level topic with a one-line summary. */
    fun index(): String {
        val width = topLevelTopics.maxOf { it.length }
        return buildString {
            appendLine("mops explain — reference for mops Notations (the textual formats mops exchanges with agents).")
            appendLine()
            appendLine("Topics:")
            for (topic in topLevelTopics) {
                appendLine("  ${topic.padEnd(width)}  ${summaryOf(topic)}")
            }
            appendLine()
            appendLine("Run `mops explain <topic>` for a page; `mops explain edit` lists the edit operations.")
        }
    }

    /** Returns the verbatim page for [path], or throws [UnknownTopicException] when it does not resolve. */
    fun page(path: String): String {
        val topic = resolve(path) ?: throw unknownTopic(path)
        return readResource(topic) ?: throw unknownTopic(path)
    }

    /**
     * Resolves [path] to a canonical topic name, or returns null.
     *
     * Besides the canonical names, accepts a top-level topic qualified by any valid topic prefix (e.g. `edit.target`
     * or `edit.delete.target` for `target`): pages cross-reference top-level topics by bare name, and readers of an
     * `edit.*` page naturally qualify those names with the namespace they are in.
     */
    private fun resolve(path: String): String? {
        if (path in allTopics) return path
        val lastDot = path.lastIndexOf('.')
        if (lastDot < 0) return null
        val prefix = path.substring(0, lastDot)
        val leaf = path.substring(lastDot + 1)
        return leaf.takeIf { prefix in allTopics && leaf in topLevelTopics }
    }

    /** Returns the build-generated JSON Schema for the edit-batch Notation, embedded as a classpath resource. */
    fun editSchema(): String =
        checkNotNull(javaClass.getResourceAsStream("/model-edit.schema.json")) { "generated edit schema missing" }
            .bufferedReader().use { it.readText() }

    private fun summaryOf(topic: String): String {
        val firstLine = (readResource(topic) ?: return "").lineSequence().firstOrNull().orEmpty().trim()
        val dash = firstLine.indexOf('—')
        return if (dash >= 0) firstLine.substring(dash + 1).trim() else firstLine
    }

    private fun readResource(path: String): String? =
        javaClass.getResourceAsStream("/explain/$path.txt")?.bufferedReader()?.use { it.readText() }

    private fun unknownTopic(path: String): UnknownTopicException {
        val parts = path.split(".")
        val parentPrefix = parts.dropLast(1).joinToString(".")
        val siblings = allTopics.filter { candidate ->
            val candidateParts = candidate.split(".")
            candidateParts.size == parts.size && candidateParts.dropLast(1).joinToString(".") == parentPrefix
        }.ifEmpty { allTopics }
        // Qualified top-level aliases (see resolve) are invocable too, so offer them as suggestions.
        val aliases = if (parentPrefix in allTopics) topLevelTopics.map { "$parentPrefix.$it" } else emptyList()
        // Nearest sibling by edit distance, but credit a shared prefix so `edit.addNode` suggests `edit.addChild`
        // (which shares the `edit.add` prefix) over an equidistant `edit.moveAsChild`.
        val suggestion = (siblings + aliases).minByOrNull { levenshtein(path, it) - commonPrefixLength(path, it) }
        val didYouMean = suggestion?.let { "did you mean $it? " }.orEmpty()
        return UnknownTopicException("unknown topic \"$path\" — ${didYouMean}valid: ${siblings.joinToString(", ")}")
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        var i = 0
        while (i < limit && a[i] == b[i]) i++
        return i
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            for (k in prev.indices) prev[k] = curr[k]
        }
        return prev[b.length]
    }
}
