package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeCatalogRequest
import com.specificlanguages.mops.protocol.CodeCatalogResponse

object CodeCatalog {
    private data class Entry(val receiver: String, val name: String, val signature: String, val access: String, val summary: String) { val path get() = "$receiver.$name" }
    private val entries = listOf(
        Entry("mops.parsing", "java", "mops.parsing.java: JavaSnippetParser", "none", "Java 8 snippet parser. Its insertion methods require command access and return nodes plus unresolved native nodes; this is not a full model check."),
        Entry("mops.parsing.java", "addJavaClassesFromString", "mops.parsing.java.addJavaClassesFromString(SModel model, String source): JavaParsingResult", "command", "Parse a compilation unit and add its classifier roots."),
        Entry("mops.parsing.java", "addJavaMembersFromString", "mops.parsing.java.addJavaMembersFromString(SNode classifier, String source, SNode beforeMember = null): JavaParsingResult", "command", "Parse fields, constructors, methods, and nested classes into a classifier."),
        Entry("mops.parsing.java", "addJavaStatementsFromString", "mops.parsing.java.addJavaStatementsFromString(SNode statementList, String source, SNode beforeStatement = null): JavaParsingResult", "command", "Parse statements into a statement list."),
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
        Entry("SNode", "child", "SNode.child: NodeChild", "none", "Live indexed accessor. child[role] returns one SNode or null, and throws for multiple children. Assignment replaces the role with one node; null clears it. Reads require model access; writes require command access. Child access uses native SNode operations; SNodeAccessUtil has no child APIs. Declared cardinality and containment constraints are not checked."),
        Entry("SNode", "children", "SNode.children: NodeChildren", "none", "Live indexed accessor. children[role] returns an immutable list of current children in order, empty for a missing role. Assign a list to replace the role, or [] to clear. New children must be detached; existing children in the role may be retained or reordered. Reads require model access; writes require command access. Unknown writes fail. Child access uses native SNode operations; SNodeAccessUtil has no child APIs. Declared cardinality and containment constraints are not checked. Shadows native getChildren(); use getChildren(containmentLink) for native iteration."),
        Entry("SNode", "references", "SNode.references: NodeReferences", "none", "Live indexed accessor. references[role] returns SReference or null; use targetNode to resolve it. Assign an SNode, SNodeReference, or SReference; null clears it. Reads require model access; writes require command access. Unknown writes fail. Assignments resolve the target and use SNodeAccessUtil.setReferenceTarget with MPS reference setter hooks. Unresolved targets fail without changing the reference; SReference assignments use the resolved target, not dynamic resolution information. Shadows native getReferences(); use getReference(referenceLink) for native access."),
        Entry("SNode", "properties", "SNode.properties: NodeProperties", "none", "Live indexed accessor: properties[name] reads with model access; properties[name] = value writes with command access. Includes inherited properties. Unknown property reads return null; unset values follow MPS getter and data type defaults. Unknown writes fail. Assign null to request clearing through the setter. Reads and writes use SNodeAccessUtil.getProperty/setProperty to invoke MPS property getter/setter handlers while retaining serialized string values. In Groovy this shadows native getProperties(); descriptors are available through node.concept.properties."),
        Entry("mops.search", "eachUsageOf", "mops.search.eachUsageOf(SNode node, SearchScope scope, Closure body): void", "read", "Stream native SReference values."),
        Entry("mops.search", "eachInstanceOf", "mops.search.eachInstanceOf(SAbstractConcept concept, SearchScope scope, boolean exact = false, Closure body): void", "read", "Stream native SNode values; subconcepts are included by default."),
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
        val names = when (subject) {
            is String -> listOf(subject)
            is Mops -> listOf("mops")
            is MopsParsing -> listOf("mops.parsing")
            is MopsSearch -> listOf("mops.search")
            is JavaSnippetParser -> listOf("mops.parsing.java")
            is Class<*> -> typeNames(subject)
            else -> typeNames(subject.javaClass)
        }
        return entries.filter { entry -> names.any { name -> entry.path == name || entry.path.startsWith("$name.") || entry.receiver == name } }
            .also { require(it.isNotEmpty()) { "unknown Code Mode extension: $subject" } }
    }
    private fun typeNames(type: Class<*>): List<String> = sequence {
        var current: Class<*>? = type
        while (current != null) { yield(current.name); yield(current.simpleName); current.interfaces.forEach { yield(it.name); yield(it.simpleName) }; current = current.superclass }
    }.toList()
}
