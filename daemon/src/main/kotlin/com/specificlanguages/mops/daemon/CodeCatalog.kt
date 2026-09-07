package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeCatalogRequest
import com.specificlanguages.mops.protocol.CodeCatalogResponse

object CodeCatalog {
    private data class Entry(val receiver: String, val name: String, val signature: String, val access: String, val summary: String) { val path get() = "$receiver.$name" }
    private val entries = listOf(
        Entry("Project", "read", "Project.read(Closure<T> body): T", "none", "Run a non-nesting MPS read action."),
        Entry("Project", "command", "Project.command(Closure<T> body): T", "none", "Run an MPS command and save after successful completion. Commands are non-transactional."),
        Entry("Project", "module", "Project.module(String target): SModule", "read", "Resolve exactly one repository module."),
        Entry("Project", "model", "Project.model(String target): SModel", "read", "Resolve exactly one model."),
        Entry("Project", "node", "Project.node(String reference): SNode", "read", "Resolve exactly one node reference."),
        Entry("Project", "concept", "Project.concept(String name): SAbstractConcept", "read", "Resolve exactly one concept."),
        Entry("Project", "make", "Project.make(): MakeResponse", "extra", "Make every generatable project module."),
        Entry("Project", "createLanguage", "Project.createLanguage(String name, Map options = [:]): Language", "command", "Create a language."),
        Entry("Project", "createSolution", "Project.createSolution(String name, Map options = [:]): Solution", "command", "Create a solution."),
        Entry("Project", "createDevkit", "Project.createDevkit(String name, Map options = [:]): DevKit", "command", "Create a devkit."),
        Entry("Language", "createGenerator", "Language.createGenerator(String alias, Map options = [:]): Generator", "command", "Create a generator for this language."),
        Entry("SModule", "createModel", "SModule.createModel(String name, boolean filePerRoot = false): SModel", "command", "Create a model in this module."),
        Entry("SNode", "render", "SNode.render(): String", "extra", "Render this node with its MPS editor."),
        Entry("global", "eachUsageOf", "eachUsageOf(SNode node, SearchScope scope, Closure body): void", "read", "Stream native SReference values."),
        Entry("global", "eachInstanceOf", "eachInstanceOf(SAbstractConcept concept, SearchScope scope, boolean exact = false, Closure body): void", "read", "Stream native SNode values; subconcepts are included by default."),
        Entry("global", "help", "help(Object subject = null): String", "none", "Show this Code Mode Reference."),
    ).sortedBy { it.path }

    fun response(request: CodeCatalogRequest) = CodeCatalogResponse(if (request.json) json(request.path) else text(request.path))
    fun text(subject: Any?): String = buildString {
        appendLine("Code Mode Reference (mops-code-mode 1.0)")
        select(subject).forEach { appendLine(); appendLine("${it.signature}  [${it.access}]"); appendLine(it.summary) }
    }.trimEnd()
    private fun json(subject: Any?): String = select(subject).joinToString(prefix = "[", postfix = "]") {
        "{\"path\":\"${it.path}\",\"signature\":\"${it.signature}\",\"access\":\"${it.access}\",\"bundle\":\"mops-code-mode:1.0\"}"
    }
    private fun select(subject: Any?): List<Entry> {
        if (subject == null) return entries
        val names = when (subject) { is String -> listOf(subject); is Class<*> -> typeNames(subject); else -> typeNames(subject.javaClass) }
        return entries.filter { entry -> names.any { name -> entry.path == name || entry.path.startsWith("$name.") || entry.receiver == name } }
            .also { require(it.isNotEmpty()) { "unknown Code Mode extension: $subject" } }
    }
    private fun typeNames(type: Class<*>): List<String> = sequence {
        var current: Class<*>? = type
        while (current != null) { yield(current.name); yield(current.simpleName); current.interfaces.forEach { yield(it.name); yield(it.simpleName) }; current = current.superclass }
    }.toList()
}
