package com.specificlanguages.mops.daemon

/**
 * Logic. Code Mode Reference entries for the `SNode` extensions: rendering, containment navigation, and indexed
 * accessors.
 *
 * Hides: how the help describes what the indexed accessors share — lookup by name and its failures, how
 * reads and writes of each feature kind reach MPS, the access they need, and how an accessor shadows a native getter.
 */
internal object CodeCatalogNodeEntries {
    /** The native collection that an accessor shadows: what iterating it yields, and the native calls that remain. */
    private class Shadowed(val iterates: String, val nativeAlternatives: String)

    /**
     * An indexed node accessor such as `node.properties`, documented by its own [semantics] (what indexing returns and
     * what assignment accepts) followed by what it shares with the other accessors of its [kind].
     */
    private class IndexedAccessor(
        val name: String,
        val type: String,
        val kind: NodeFeatureKind,
        val semantics: String,
        val shadowed: Shadowed? = null,
    ) {
        fun entry() = CodeCatalog.Entry(
            "SNode", name, "SNode.$name: $type", "none",
            listOfNotNull("Live indexed accessor.", semantics, lookup(), featureAccess(kind), shadowing()).joinToString(" "),
            readAccess = "inside project.read or project.command",
            writeAccess = "inside project.command",
        )

        private fun lookup(): String =
            "Names include inherited ${kind.plural}. Unknown ${kind.plural} fail on read and write, listing the known ones."

        private fun shadowing(): String? = shadowed?.let {
            val getter = "get${name.replaceFirstChar(Char::uppercase)}()"
            "Shadows native $getter, so an explicit $getter call returns this accessor; it iterates ${it.iterates} " +
                "like the native collection, so collect, findAll, each, and size() work on it. ${it.nativeAlternatives}"
        }
    }

    /** How reads and writes of a feature kind reach MPS, which every accessor of that kind shares. */
    private fun featureAccess(kind: NodeFeatureKind): String = when (kind) {
        NodeFeatureKind.PROPERTY ->
            "Reads and writes invoke MPS property getter/setter handlers and use persisted string values, except for " +
                "enumeration properties, which read as the literal name and accept a literal name or a presentation " +
                "that only one literal has; any other enumeration value fails and lists the literals."
        NodeFeatureKind.CHILD_ROLE ->
            "Child access uses native SNode operations; SNodeAccessUtil has no child APIs. Declared cardinality and " +
                "containment constraints are not checked."
        NodeFeatureKind.REFERENCE_ROLE ->
            "Assignments resolve the target and use SNodeAccessUtil.setReferenceTarget with MPS reference setter " +
                "hooks. Unresolved targets fail without changing the reference; SReference assignments use the " +
                "resolved target, not dynamic resolution information."
    }

    private val accessors = listOf(
        IndexedAccessor(
            "child", "NodeChild", NodeFeatureKind.CHILD_ROLE,
            "child[role] returns one SNode or null, and throws for multiple children. Assignment replaces the role " +
                "with one node; null clears it.",
        ),
        IndexedAccessor(
            "children", "NodeChildren", NodeFeatureKind.CHILD_ROLE,
            "children[role] returns an immutable list of current children in order, empty for a known role without " +
                "children. Assign a list to replace the role, or [] to clear. New children must be detached; existing " +
                "children in the role may be retained or reordered.",
            Shadowed("all children across roles in native order", "getChildren(containmentLink) remains native."),
        ),
        IndexedAccessor(
            "references", "NodeReferences", NodeFeatureKind.REFERENCE_ROLE,
            "references[role] returns SReference or null; use targetNode to resolve it. Assign an SNode, " +
                "SNodeReference, or SReference; null clears it.",
            Shadowed(
                "all set SReference values",
                "getReference(referenceLink) and SNodeOperations.getReferences(node) remain native.",
            ),
        ),
        IndexedAccessor(
            "properties", "NodeProperties", NodeFeatureKind.PROPERTY,
            "properties[name] returns the value as a string, or null when there is no value; unset values follow MPS " +
                "getter and data type defaults. Assign a string, or null to request clearing through the setter.",
            Shadowed(
                "the SProperty descriptors of set properties",
                "Declared descriptors are available through node.concept.properties.",
            ),
        ),
    )

    val entries = listOf(
        CodeCatalog.Entry("SNode", "render", "SNode.render(): String", "extra", "Render this node with its MPS editor."),
        CodeCatalog.Entry("SNode", "descendants", "SNode.descendants: List<SNode>", "read", "Immutable snapshot of containment descendants in depth-first pre-order following native child order, excluding this node. References are not followed. Retained nodes keep native MPS validity semantics."),
        CodeCatalog.Entry("SNode", "ancestors", "SNode.ancestors: List<SNode>", "read", "Immutable snapshot of containment ancestors from parent to root, excluding this node. Retained nodes keep native MPS validity semantics."),
    ) + accessors.map { it.entry() }
}
