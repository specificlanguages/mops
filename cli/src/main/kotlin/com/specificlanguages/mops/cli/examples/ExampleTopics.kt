package com.specificlanguages.mops.cli.examples

object ExampleTopics {
    val topics = listOf(
        "setup", "navigation", "search", "editing", "editing.creation", "editing.nodes",
        "editing.references", "editing.java", "editing.json", "validation",
    )

    fun index(): String = pageResource("README") + "\nTopics: ${topics.joinToString(", ")}\n"

    fun page(topic: String): String {
        if (topic == "all") return index() + topics.joinToString("\n", transform = ::pageResource)
        require(topic in topics) { "Unknown examples topic '$topic'; available: ${topics.joinToString(", ")}, all" }
        return pageResource(topic)
    }

    private fun pageResource(topic: String): String =
        checkNotNull(javaClass.getResourceAsStream("/examples/$topic.md")) { "Missing examples topic: $topic" }
            .bufferedReader().use { it.readText() }
}
