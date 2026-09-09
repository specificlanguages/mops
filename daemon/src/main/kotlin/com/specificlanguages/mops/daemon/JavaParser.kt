package com.specificlanguages.mops.daemon

import groovy.lang.GroovyObjectSupport
import jetbrains.mps.java.core.newparser.FeatureKind
import jetbrains.mps.java.core.newparser.JavaParser
import jetbrains.mps.java.core.newparser.JavaToMpsConverter
import jetbrains.mps.java.core.newparser.YetUnknownResolver
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.smodel.ModelDependencyUpdate
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode

/** The attached output of one Java snippet parsing operation. */
class JavaParsingResult(
    val nodes: List<SNode>,
    val unresolved: List<SNode>,
) : AbstractMap<String, Any?>() {
    override val entries: Set<Map.Entry<String, Any?>> = mapOf("nodes" to nodes, "unresolved" to unresolved).entries
}

/** Parses Java source into native MPS destinations and resolves the inserted subtrees. */
class JavaSnippetParser : GroovyObjectSupport() {
    override fun invokeMethod(name: String, arguments: Any?): Any? {
        val args = when (arguments) {
            is Array<*> -> arguments
            null -> emptyArray()
            else -> arrayOf(arguments)
        }
        return when (name) {
            "addJavaClassesFromString" -> requireArguments(name, args, 2) { addJavaClassesFromString(it[0]!!, it[1] as String) }
            "addJavaMembersFromString" -> requireArguments(name, args, 2, 3) {
                addJavaMembersFromString(it[0]!!, it[1] as String, it.getOrNull(2))
            }
            "addJavaStatementsFromString" -> requireArguments(name, args, 2, 3) {
                addJavaStatementsFromString(it[0]!!, it[1] as String, it.getOrNull(2))
            }
            else -> super.invokeMethod(name, arguments)
        }
    }

    private fun requireArguments(name: String, arguments: Array<out Any?>, vararg counts: Int, call: (Array<out Any?>) -> Any?): Any? {
        require(arguments.size in counts) { "$name expects ${counts.joinToString(" or ")} arguments" }
        return call(arguments)
    }
    fun addJavaClassesFromString(model: Any, source: String): JavaParsingResult {
        requireCommand("JavaSnippetParser.addJavaClassesFromString")
        require(model is SModel) { "destination must be an MPS model" }
        val parsed = parse(source, FeatureKind.CLASS, null)
        val packageName = parsed.`package`
        require(packageName == null || packageName == model.name.longName) {
            "source package $packageName does not match destination model package ${model.name.longName}"
        }
        parsed.nodes.forEach(model::addRootNode)
        return resolve(model, parsed.nodes, FeatureKind.CLASS)
    }

    fun addJavaMembersFromString(classifier: Any, source: String): JavaParsingResult =
        addJavaMembersFromString(classifier, source, null)

    fun addJavaMembersFromString(classifier: Any, source: String, beforeMember: Any?): JavaParsingResult {
        requireCommand("JavaSnippetParser.addJavaMembersFromString")
        require(classifier is SNode) { "destination must be an MPS node" }
        require(beforeMember == null || beforeMember is SNode) { "insertion anchor must be an MPS node" }
        require(classifier.isInstanceOfConcept(classifierConcept)) { "destination must be a BaseLanguage classifier" }
        val role = memberLink
        requireAnchor(classifier, role, beforeMember)
        val parsed = parse(source, FeatureKind.CLASS_CONTENT, classifier)
        insert(classifier, role, parsed.nodes, beforeMember)
        return resolve(requireNotNull(classifier.model), parsed.nodes, FeatureKind.CLASS_CONTENT)
    }

    fun addJavaStatementsFromString(statementList: Any, source: String): JavaParsingResult =
        addJavaStatementsFromString(statementList, source, null)

    fun addJavaStatementsFromString(statementList: Any, source: String, beforeStatement: Any?): JavaParsingResult {
        requireCommand("JavaSnippetParser.addJavaStatementsFromString")
        require(statementList is SNode) { "destination must be an MPS node" }
        require(beforeStatement == null || beforeStatement is SNode) { "insertion anchor must be an MPS node" }
        require(statementList.isInstanceOfConcept(statementListConcept)) { "destination must be a BaseLanguage statement list" }
        val role = statementLink
        requireAnchor(statementList, role, beforeStatement)
        val parsed = parse(source, FeatureKind.STATEMENTS, statementList)
        insert(statementList, role, parsed.nodes, beforeStatement)
        return resolve(requireNotNull(statementList.model), parsed.nodes, FeatureKind.STATEMENTS)
    }

    private fun parse(source: String, kind: FeatureKind, context: SNode?): JavaParser.JavaParseResult = try {
        JavaParser().parse(source, kind, context, false).also { result ->
            require(result.errorMsg == null) { "Java syntax error: ${result.errorMsg}" }
        }
    } catch (failure: Exception) {
        throw IllegalArgumentException("Java syntax error: ${failure.message ?: failure.javaClass.simpleName}", failure)
    }

    private fun insert(parent: SNode, role: SContainmentLink, nodes: List<SNode>, before: SNode?) {
        nodes.forEach { node ->
            if (before == null) parent.addChild(role, node) else parent.insertChildBefore(role, node, before)
        }
    }

    private fun requireAnchor(parent: SNode, role: SContainmentLink, anchor: SNode?) {
        if (anchor != null) require(anchor.parent === parent && anchor.containmentLink == role) {
            "insertion anchor must be a direct child in the destination role"
        }
    }

    private fun resolve(model: SModel, inserted: List<SNode>, kind: FeatureKind): JavaParsingResult {
        val importsBefore = ModelImports(model).importedModels.toSet()
        val monitor = EmptyProgressMonitor()
        JavaToMpsConverter(model, requireNotNull(model.repository), jetbrains.mps.messages.IMessageHandler.NULL_HANDLER)
            .tryResolveRefs(inserted, kind, monitor)
        YetUnknownResolver(model, inserted).tryResolveUnknowns(monitor)
        JavaToMpsConverter(model, requireNotNull(model.repository), jetbrains.mps.messages.IMessageHandler.NULL_HANDLER)
            .tryResolveRefs(inserted, kind, monitor)
        ModelDependencyUpdate(model, inserted).updateUsedLanguages().updateImportedModels(null)
        addDependenciesForNewImports(model, importsBefore)
        val attached = inserted.filter { it.model === model }
        return JavaParsingResult(attached, unresolved(attached))
    }

    private fun addDependenciesForNewImports(model: SModel, importsBefore: Set<org.jetbrains.mps.openapi.model.SModelReference>) {
        val module = model.module as? AbstractModule ?: return
        ModelImports(model).importedModels.asSequence().filter { it !in importsBefore }.forEach { reference ->
            if (module.scope.resolve(reference) == null) {
                val target = reference.resolve(requireNotNull(model.repository)) ?: return@forEach
                target.module?.moduleReference?.let { module.addDependency(it, false) }
            }
        }
    }

    private fun unresolved(roots: List<SNode>): List<SNode> = roots.asSequence()
        .flatMap { root -> sequenceOf(root) + root.descendants() }
        .filter { node -> node.isInstanceOfConcept(yetUnresolvedConcept) || node.references.any { it.targetNode == null } }
        .toList()

    private fun SNode.descendants(): Sequence<SNode> = sequence {
        children.forEach { child -> yield(child); yieldAll(child.descendants()) }
    }

    private companion object {
        val classifierConcept = MetaAdapterFactory.getConcept(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0x101d9d3ca30L, "jetbrains.mps.baseLanguage.structure.Classifier")
        val statementListConcept = MetaAdapterFactory.getConcept(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0xf8cc56b200L, "jetbrains.mps.baseLanguage.structure.StatementList")
        val yetUnresolvedConcept = MetaAdapterFactory.getInterfaceConcept(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0x70ea1dc4c5721865L, "jetbrains.mps.baseLanguage.structure.IYetUnresolved")
        val memberLink = MetaAdapterFactory.getContainmentLink(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0x101d9d3ca30L, 0x4a9a46de59132803L, "member")
        val statementLink = MetaAdapterFactory.getContainmentLink(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0xf8cc56b200L, 0xf8cc6bf961L, "statement")
    }
}
