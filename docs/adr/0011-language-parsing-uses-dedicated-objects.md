# Language parsing uses dedicated objects

> **Status: accepted; amended by ADR-0012.**

Language parsing groups its operations on a dedicated parser object whose methods accept native MPS destinations. This is an exception to ADR-0008's preference for extensions on native MPS receivers: adding parsing support for more languages should not continually expand the methods exposed on models and nodes. Java is the first implementation; other contributors can expose their own language-specific parser objects without implementing a shared parsing contract.

Code Mode exposes the Java parser through the `mops.parsing.java` extension getter. This uses the existing extension mechanism without introducing a mutable registry of global objects.

The initial methods are `addJavaClassesFromString(model, source)`, `addJavaMembersFromString(classifier, source[, beforeMember])`, and `addJavaStatementsFromString(statementList, source[, beforeStatement])`. Calls require a command access block. Members include nested classes. Omitted anchors append; supplied anchors must be direct children in the destination containment role. All methods return a Java Parsing Result.

An explicit source package must match the destination model's package; a mismatch is rejected before insertion to avoid silently changing package meaning. Source without a package declaration uses the destination package.

Parsing completes before insertion and rejects syntax errors without accepting recovered partial output. After insertion, resolution processes only the inserted subtrees; existing code supplies context but is not repaired. The result exposes `nodes`, containing the final inserted nodes after any resolution replacements, and `unresolved`, describing remaining unresolved references and ambiguous constructs with their native nodes. This report is not a full model check. Execution failures retain Code Mode's native partial-change semantics.

The operation adds used languages, preserves Java import metadata needed for resolution, and adds required model imports. For concrete resolved targets, it also adds non-reexported module dependencies when existing visibility is insufficient. These dependency additions are limited to imports needed by the inserted code; unrelated existing imports are not repaired, and unresolved names do not trigger library discovery or downloads.

The first implementation targets Code Mode and the three stock MPS parsing modes, with their Java 8 syntax limit. Declarative batch-edit integration is deferred. Parser and resolver behavior in a headless command, including replacement tracking and dependency updates, requires runtime validation; source research is recorded in `docs/mps/java-parsing-and-resolution.md`.
