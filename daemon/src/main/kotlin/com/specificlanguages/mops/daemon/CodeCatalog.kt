package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeCatalogRequest
import com.specificlanguages.mops.protocol.*

object CodeCatalog {
    private data class Entry(val receiver: String, val name: String, val signature: String, val access: String, val summary: String) {
        val path get() = "$receiver.$name"
        fun documented(): CodeHelpEntry {
            val arguments = signature.substringAfter('(', "").substringBeforeLast(')', "")
            val parameters = if ('(' !in signature || arguments.isEmpty()) emptyList() else arguments.split(", ").map { argument ->
                val declaration = argument.substringBefore(" = ")
                val type = declaration.substringBeforeLast(' ')
                CodeHelpParameter(declaration.substringAfterLast(' '), type.removeSuffix("?"), type.endsWith("?") || argument.endsWith("= null"), argument.substringAfter(" = ", "").ifEmpty { null }, if (declaration.substringAfterLast(' ') == "body") when (path) { "mops.search.eachUsageOf" -> "Receives one SReference"; "mops.search.eachInstanceOf" -> "Receives one SNode"; else -> "Returns the value T returned by the access block" } else null)
            }
            val result = signature.substringAfterLast(": ")
            return CodeHelpEntry(path, signature, summary, if (path in listOf("Project.read", "Project.command")) "outside an access block [none]" else accessDescription(CodeHelpApi.access(receiver, name, access)), parameters,
                result.removeSuffix("?"), result.endsWith("?"), options(path), examples(path),
                listOf(result.removeSuffix("?").removePrefix("List<").removeSuffix(">")).filter { it !in listOf("T", "void", "String") },
                if (name in listOf("properties", "child", "children", "references")) "inside project.read or project.command" else null,
                if (name in listOf("properties", "child", "children", "references")) "inside project.command" else null)
        }
    }
    private val entries = listOf(
        Entry("mops.testing", "run", "mops.testing.run(Object selection = project, Map options = [:]): TestRunReport", "extra", "Build and run project/module/model/test-node tests in an isolated process. Options: build (true), timeout (900 seconds; 0 disables). Returns failures and partial results in a saved report."),
        Entry("mops.editing", "build", "mops.editing.build: MopsEditingBuild", "none", "Editing operations for MPS build projects."),
        Entry("mops.editing.build", "reloadModulesFromDisk", "mops.editing.build.reloadModulesFromDisk(SNode node): BuildModuleReloadResult", "command", "Reload the nearest BuildProject's existing module entries from their descriptor files. Returns ordered warnings and errors; successful partial updates are retained."),
        Entry("mops.parsing", "java", "mops.parsing.java: JavaSnippetParser", "none", "Java 8 snippet parser. Its insertion methods require command access and return nodes plus unresolved native nodes; this is not a full model check."),
        Entry("mops.parsing.java", "addJavaClassesFromString", "mops.parsing.java.addJavaClassesFromString(SModel model, String source): JavaParsingResult", "command", "Parse a compilation unit and add its classifier roots."),
        Entry("mops.parsing.java", "addJavaMembersFromString", "mops.parsing.java.addJavaMembersFromString(SNode classifier, String source, SNode beforeMember = null): JavaParsingResult", "command", "Parse fields, constructors, methods, and nested classes into a classifier."),
        Entry("mops.parsing.java", "addJavaStatementsFromString", "mops.parsing.java.addJavaStatementsFromString(SNode statementList, String source, SNode beforeStatement = null): JavaParsingResult", "command", "Parse statements into a statement list."),
        Entry("Project", "read", "Project.read(Closure<T> body): T", "none", "Run a non-nesting MPS read action."),
        Entry("Project", "command", "Project.command(Closure<T> body): T", "none", "Run an MPS command and save after successful completion. Commands are non-transactional."),
        Entry("Project", "make", "Project.make(): MakeResponse", "extra", "Make every generatable project module."),
        Entry("Project", "createLanguage", "Project.createLanguage(String name, Map options = [:]): Language", "command", "Create a language."),
        Entry("Project", "createSolution", "Project.createSolution(String name, Map options = [:]): Solution", "command", "Create a solution."),
        Entry("Project", "createDevkit", "Project.createDevkit(String name, Map options = [:]): DevKit", "command", "Create a devkit."),
        Entry("Language", "createGenerator", "Language.createGenerator(String alias, Map options = [:]): Generator", "command", "Create a generator for this language."),
        Entry("SModule", "createModel", "SModule.createModel(String name, boolean filePerRoot = false): SModel", "command", "Create a model in this module."),
        Entry("SNode", "render", "SNode.render(): String", "extra", "Render this node with its MPS editor."),
        Entry("SNode", "descendants", "SNode.descendants: List<SNode>", "read", "Immutable snapshot of containment descendants in depth-first pre-order following native child order, excluding this node. References are not followed. Retained nodes keep native MPS validity semantics."),
        Entry("SNode", "ancestors", "SNode.ancestors: List<SNode>", "read", "Immutable snapshot of containment ancestors from parent to root, excluding this node. Retained nodes keep native MPS validity semantics."),
        Entry("SNode", "child", "SNode.child: NodeChild", "none", "Live indexed accessor. child[role] returns one SNode or null, and throws for multiple children. Assignment replaces the role with one node; null clears it. Reads require model access; writes require command access. Child access uses native SNode operations; SNodeAccessUtil has no child APIs. Declared cardinality and containment constraints are not checked."),
        Entry("SNode", "children", "SNode.children: NodeChildren", "none", "Live indexed accessor. children[role] returns an immutable list of current children in order, empty for a missing role. Assign a list to replace the role, or [] to clear. New children must be detached; existing children in the role may be retained or reordered. Reads require model access; writes require command access. Unknown writes fail. Child access uses native SNode operations; SNodeAccessUtil has no child APIs. Declared cardinality and containment constraints are not checked. Shadows native getChildren(); use getChildren(containmentLink) for native iteration."),
        Entry("SNode", "references", "SNode.references: NodeReferences", "none", "Live indexed accessor. references[role] returns SReference or null; use targetNode to resolve it. Assign an SNode, SNodeReference, or SReference; null clears it. Reads require model access; writes require command access. Unknown writes fail. Assignments resolve the target and use SNodeAccessUtil.setReferenceTarget with MPS reference setter hooks. Unresolved targets fail without changing the reference; SReference assignments use the resolved target, not dynamic resolution information. Shadows native getReferences(); use getReference(referenceLink) for native access."),
        Entry("SNode", "properties", "SNode.properties: NodeProperties", "none", "Live indexed accessor: properties[name] reads with model access; properties[name] = value writes with command access. Includes inherited properties. Unknown property reads return null; unset values follow MPS getter and data type defaults. Unknown writes fail. Assign null to request clearing through the setter. Reads and writes use SNodeAccessUtil.getProperty/setProperty to invoke MPS property getter/setter handlers while retaining serialized string values. In Groovy this shadows native getProperties(); descriptors are available through node.concept.properties."),
        Entry("mops.lookup", "conceptByName", "mops.lookup.conceptByName(String name): SAbstractConcept?", "read", "Resolve a concept name; return null on a miss. Ambiguity, malformed names, and untrusted language runtimes remain errors."),
        Entry("mops.lookup", "requireConceptByName", "mops.lookup.requireConceptByName(String name): SAbstractConcept", "read", "Resolve exactly one concept; throw on a miss."),
        Entry("mops.search", "eachUsageOf", "mops.search.eachUsageOf(SNode node, SearchScope scope, Closure body): void", "read", "Stream native SReference values; body receives one SReference."),
        Entry("mops.search", "eachInstanceOf", "mops.search.eachInstanceOf(SAbstractConcept concept, SearchScope scope, boolean exact = false, Closure body): void", "read", "Stream native SNode values; body receives one SNode. Subconcepts are included by default."),
        Entry("mops.lookup", "model", "mops.lookup.model(String target): SModel?", "read", "Resolve a model name or serialized reference; return null on a miss and reject ambiguity."),
        Entry("mops.lookup", "requireModel", "mops.lookup.requireModel(String target): SModel", "read", "Resolve exactly one model; throw on a miss."),
        Entry("mops.lookup", "module", "mops.lookup.module(String target): SModule?", "read", "Resolve a module name or serialized reference; return null on a miss and reject ambiguity."),
        Entry("mops.lookup", "requireModule", "mops.lookup.requireModule(String target): SModule", "read", "Resolve exactly one module; throw on a miss."),
        Entry("mops.lookup", "node", "mops.lookup.node(String reference): SNode?", "read", "Resolve a node reference; return null on a miss and reject malformed references."),
        Entry("mops.lookup", "requireNode", "mops.lookup.requireNode(String reference): SNode", "read", "Resolve exactly one node; throw on a miss."),
        Entry("global", "help", "help(Object subject = null): String", "none", "Show this Code Mode Reference."),
    ).sortedBy { it.path }

    fun response(request: CodeCatalogRequest) = CodeCatalogResponse(if (request.json) json(request.path) else text(request.path))

    fun text(subject: Any?): String {
        val document = document(subject)
        return buildString {
            appendLine("Code Mode Reference (mops-code-mode 1.0)")
            appendLine("Access blocks cannot nest. command saves after success and is non-transactional.")
            if (document.index.isNotEmpty()) {
                appendLine("Use help('path') to inspect an operation or type; help(object) and help(Class) also work.")
                document.index.forEach { appendLine("  $it") }
            }
            document.entries.forEach { entry ->
                appendLine(); appendLine(entry.signature)
                appendLine("Access: ${entry.access}")
                appendLine(entry.summary)
                entry.readAccess?.let { appendLine("Indexed reads: $it; indexed writes: ${entry.writeAccess}.") }
                entry.parameters.forEach { parameter -> appendLine("  ${parameter.name}: ${parameter.type}${if (parameter.nullable) "?" else ""}${parameter.default?.let { " = $it" }.orEmpty()}${parameter.description?.let { "; $it" }.orEmpty()}") }
                entry.options.forEach { option -> appendLine("  options.${option.name}: ${option.type} = ${option.default}; ${option.description}${if (option.values.isEmpty()) "" else "; values: ${option.values.joinToString()}"}") }
                entry.examples.forEach { appendLine("Example:\n$it") }
                if (entry.related.isNotEmpty()) appendLine("Related: ${entry.related.joinToString()}")
            }
            document.nativeType?.let { appendLine(); append(renderMembers(it)) }
        }.trimEnd()
    }

    fun json(subject: Any?): String = ProtocolJson.encodeCodeHelp(document(subject))

    internal fun document(subject: Any?): CodeHelpDocument {
        if (subject == null) return CodeHelpDocument(index = (entries.map { it.receiver } + resultMembers.keys).distinct().sorted())
        val names = when (subject) {
            is String -> CodeMemberInspector.resolveType(if (subject == "project") "Project" else subject)?.let(::typeNames) ?: listOf(if (subject == "project" || subject.startsWith("project.")) "Project" + subject.removePrefix("project") else subject)
            is Mops -> listOf("mops")
            is MopsEditing -> listOf("mops.editing")
            is MopsEditingBuild -> listOf("mops.editing.build")
            is MopsParsing -> listOf("mops.parsing")
            is MopsTesting -> listOf("mops.testing")
            is MopsSearch -> listOf("mops.search")
            is MopsLookup -> listOf("mops.lookup")
            is JavaSnippetParser -> listOf("mops.parsing.java")
            is Class<*> -> typeNames(subject)
            else -> typeNames(subject.javaClass)
        }
        var selected = entries.filter { entry -> names.any { name -> entry.path.equals(name, true) || entry.path.startsWith("$name.", true) || entry.receiver.equals(name, true) } }
        if (selected.isEmpty() && subject is String) {
            selected = entries.filter { it.name.equals(subject, true) }
            require(selected.map { it.receiver }.distinct().size <= 1) { "Ambiguous help name '$subject': ${selected.joinToString { it.path }}" }
        }
        var results = resultMembers.filterKeys { key -> names.any { it == key || it.substringAfterLast('.') == key || it.startsWith("$key.") } }.values.flatten().filter { entry -> names.any { it == entry.path.substringBeforeLast('.') || it.substringAfterLast('.') == entry.path.substringBeforeLast('.') || it == entry.path } }
        if (subject is String && selected.isEmpty() && results.isEmpty()) {
            results = resultMembers.values.flatten().filter { it.path.substringAfterLast('.').equals(subject, true) }
            require(results.map { it.path.substringBeforeLast('.') }.distinct().size <= 1) { "Ambiguous help name '$subject': ${results.joinToString { it.path }}" }
        }
        val type = when (subject) {
            is Class<*> -> subject
            is String -> CodeMemberInspector.resolveType(if (subject == "project") "Project" else subject)
            else -> if (subject is Mops || subject is MopsEditing || subject is MopsEditingBuild || subject is MopsParsing || subject is MopsTesting || subject is MopsSearch || subject is MopsLookup || subject is JavaSnippetParser) null else subject.javaClass
        }
        val native = type?.let { nativeDocument(CodeMemberInspector.inspect(it), selected) }
        require(selected.isNotEmpty() || results.isNotEmpty() || native != null) {
            val query = names.first().lowercase()
            val suggestions = entries.map { it.path }.distinct().sortedBy { editDistance(query, it.lowercase()) }.take(3)
            "Unknown Code Mode help subject '$subject'. Try: ${suggestions.joinToString()}"
        }
        return CodeHelpDocument(entries = selected.map { it.documented() } + results, nativeType = native)
    }

    private fun typeNames(type: Class<*>): List<String> = CodeMemberInspector.typeHierarchy(type).flatMap {
        listOf(it.name, it.simpleName) + when (it.simpleName) {
            "JavaSnippetParser" -> listOf("mops.parsing.java")
            "MopsEditingBuild" -> listOf("mops.editing.build")
            else -> emptyList()
        }
    }

    private fun nativeDocument(type: CodeTypeMembers, extensions: List<Entry>) = CodeHelpNativeType(
        type.type, type.hierarchy,
        type.methods.map { CodeHelpNativeMethod(it.name, it.parameters.mapIndexed { index, parameter -> CodeHelpParameter(parameter.name ?: "arg$index", parameter.type) }, it.returnType, it.declaringClass, it.inherited, it.static) },
        type.properties.map { CodeHelpNativeProperty(it.name, it.type, it.getter, it.setter, it.declaringClass, it.inherited, extensions.filter { extension -> extension.name == it.name }.map { extension -> extension.path }) },
        type.fields.map { CodeHelpNativeField(it.name, it.type, it.declaringClass, it.inherited, it.static, it.writable) },
    )

    private fun renderMembers(type: CodeHelpNativeType): String = buildString {
        appendLine("Native members of ${type.type} (signatures only; consult MPS API contracts for access requirements):")
        type.properties.forEach { member ->
            appendLine("  ${member.name}: ${member.type} [${member.declaringClass}${if (member.inherited) "; inherited" else ""}]")
            member.shadowedBy.forEach { appendLine("    Groovy property is shadowed by $it; native getter: ${member.getter}") }
        }
        type.methods.forEach { member -> appendLine("  ${member.name}(${member.parameters.joinToString { it.type + " " + it.name }}): ${member.returnType} [${member.declaringClass}${if (member.inherited) "; inherited" else ""}]") }
        type.fields.forEach { member -> appendLine("  ${member.name}: ${member.type} [${member.declaringClass}]") }
    }.trimEnd()

    private fun accessDescription(access: String) = when (access) {
        "extra" -> "outside an access block [extra]"
        "command" -> "inside project.command [command]"
        "read" -> "inside project.read or project.command [read]"
        else -> "no access block required [none]"
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, left ->
            val current = IntArray(b.length + 1); current[0] = i + 1
            b.forEachIndexed { j, right -> current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (left == right) 0 else 1) }
            previous = current
        }
        return previous.last()
    }
}
