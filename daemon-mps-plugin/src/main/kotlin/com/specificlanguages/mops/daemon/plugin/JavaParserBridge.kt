package com.specificlanguages.mops.daemon.plugin

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

/** Java parser implementation loaded through the MOPS IDEA plugin classloader. */
object JavaParserBridge {
    @JvmStatic
    fun invoke(operation: String, destination: Any, source: String, anchor: Any?): Array<Any> =
        when (operation) {
            "classes" -> addClasses(destination, source)
            "members" -> addChildren(destination, source, anchor, FeatureKind.CLASS_CONTENT, classifierConcept, memberLink)
            "statements" -> addChildren(destination, source, anchor, FeatureKind.STATEMENTS, statementListConcept, statementLink)
            else -> error("Unknown Java parser operation: $operation")
        }

    private fun addClasses(destination: Any, source: String): Array<Any> {
        require(destination is SModel) { "destination must be an MPS model" }
        val parsed = parse(source, FeatureKind.CLASS, null)
        val packageName = parsed.`package`
        require(packageName == null || packageName == destination.name.longName) {
            "source package $packageName does not match destination model package ${destination.name.longName}"
        }
        parsed.nodes.forEach(destination::addRootNode)
        return resolve(destination, parsed.nodes, FeatureKind.CLASS)
    }

    private fun addChildren(
        destination: Any,
        source: String,
        anchor: Any?,
        kind: FeatureKind,
        destinationConcept: org.jetbrains.mps.openapi.language.SAbstractConcept,
        role: SContainmentLink,
    ): Array<Any> {
        require(destination is SNode) { "destination must be an MPS node" }
        require(anchor == null || anchor is SNode) { "insertion anchor must be an MPS node" }
        require(destination.isInstanceOfConcept(destinationConcept)) {
            "destination must be a BaseLanguage ${if (kind == FeatureKind.CLASS_CONTENT) "classifier" else "statement list"}"
        }
        requireAnchor(destination, role, anchor)
        val parsed = parse(source, kind, destination)
        insert(destination, role, parsed.nodes, anchor)
        return resolve(requireNotNull(destination.model), parsed.nodes, kind)
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

    private fun resolve(model: SModel, inserted: List<SNode>, kind: FeatureKind): Array<Any> {
        val importsBefore = ModelImports(model).importedModels.toSet()
        val monitor = EmptyProgressMonitor()
        JavaToMpsConverter(model, requireNotNull(model.repository), jetbrains.mps.messages.IMessageHandler.NULL_HANDLER)
            .tryResolveRefs(inserted, kind, monitor)
        YetUnknownResolver(model, inserted).tryResolveUnknowns(monitor)
        JavaToMpsConverter(model, requireNotNull(model.repository), jetbrains.mps.messages.IMessageHandler.NULL_HANDLER)
            .tryResolveRefs(inserted, kind, monitor)
        ModelDependencyUpdate(model).updateUsedLanguages().updateImportedModels(null)
        addDependenciesForNewImports(model, importsBefore)
        val attached = inserted.filter { it.model === model }
        return arrayOf(attached, unresolved(attached))
    }

    private fun addDependenciesForNewImports(
        model: SModel,
        importsBefore: Set<org.jetbrains.mps.openapi.model.SModelReference>,
    ) {
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

    private val classifierConcept = MetaAdapterFactory.getConcept(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0x101d9d3ca30L, "jetbrains.mps.baseLanguage.structure.Classifier")
    private val statementListConcept = MetaAdapterFactory.getConcept(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0xf8cc56b200L, "jetbrains.mps.baseLanguage.structure.StatementList")
    private val yetUnresolvedConcept = MetaAdapterFactory.getInterfaceConcept(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0x70ea1dc4c5721865L, "jetbrains.mps.baseLanguage.structure.IYetUnresolved")
    private val memberLink = MetaAdapterFactory.getContainmentLink(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0x101d9d3ca30L, 0x4a9a46de59132803L, "member")
    private val statementLink = MetaAdapterFactory.getContainmentLink(0xf3061a5392264cc5UL.toLong(), 0xa443f952ceaf5816UL.toLong(), 0xf8cc56b200L, 0xf8cc6bf961L, "statement")
}
