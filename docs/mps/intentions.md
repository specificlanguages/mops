# Discovering and executing intentions

Verified public signatures with `javap` against `com.jetbrains:mps:2025.1.2`; behavior read from JetBrains/MPS
2025.1 source at commit `f4d90532bcac5e0339b3161cec38abf49567cffb`. Source paths below are repository-root-relative.
No intention discovery or execution runtime probe was run: threading, lifecycle, and headless compatibility remain
unverified for this API. The execution recipe below follows the editor's source, rather than claiming a tested
headless contract.

## API and artifacts

`jetbrains.mps.intentions.IntentionsManager` ships in `lib/mps-editor.jar`:

```java
public static IntentionsManager getInstance();
public synchronized Collection<Pair<IntentionExecutable, SNode>> getAvailableIntentions(
    QueryDescriptor query, @NotNull SNode node, EditorContext context);
@NotNull
public List<IntentionExecutable> getIntentionsById(SNode node, EditorContext editorContext, String id);
@NotNull
public synchronized Map<SLanguage, Collection<IntentionFactory>> getAllIntentionFactories();
```

`Pair` is `jetbrains.mps.util.Pair`; its fields `o1` and `o2` hold the executable and applicable node respectively.
The following interfaces are in `jetbrains.mps.openapi.intentions`, in `lib/mps-editor-api.jar`:

```java
public interface IntentionDescriptor {
  String getPresentation();
  String getPersistentStateKey();
  Kind getKind();
  boolean isAvailableInChildNodes();
  @Nullable SNodeReference getIntentionNodeReference();
}

public interface IntentionFactory extends IntentionDescriptor {
  boolean isSurroundWith();
  Collection<? extends IntentionExecutable> instances(SNode node, EditorContext editorContext);
}

public interface IntentionExecutable {
  String getDescription(SNode node, EditorContext editorContext);
  void execute(SNode node, EditorContext editorContext);
  IntentionDescriptor getDescriptor();
  default boolean isApplicable(SNode node, EditorContext editorContext); // default returns true
}

public interface ParameterizedIntentionExecutable {
  Object getParameter();
}
```

`ParameterizedIntentionExecutable` is a separate interface, not a subtype of `IntentionExecutable`. Generated
parameterized executable classes implement both. Except for the annotations shown, these interface signatures do
not declare nullability. In particular, `getParameter()` does not promise a non-null result.

## Discovery on a node

```java
IntentionsManager.QueryDescriptor query = new IntentionsManager.QueryDescriptor();
query.setEnabledOnly(true);
query.setCurrentNodeOnly(true);
query.setSurroundWith(false);
Collection<Pair<IntentionExecutable, SNode>> available =
    IntentionsManager.getInstance().getAvailableIntentions(query, node, editorContext);
```

All three flags default to `false`:

| Flag | Meaning |
| --- | --- |
| `enabledOnly` | Exclude intentions disabled in application settings. Set explicitly for normal user-facing discovery. |
| `currentNodeOnly` | Limit discovery to the supplied node. When false, also walk its ancestors. |
| `surroundWith` | Select surround-with factories when true, ordinary factories when false; this is an exclusive filter. |

The manager performs these steps:

1. Collect intention aspects and migration scripts from the model's used languages and their extended languages.
2. Visit the node's concept, superconcepts, and interfaces, retrieving factories registered for each concept.
3. Apply enabled/surround-with filters. For ancestor nodes, require `isAvailableInChildNodes()`.
4. Call `factory.instances(node, context)` and retain executables whose `isApplicable(node, context)` returns true.
5. Include editor quick fixes obtained from the editor highlight manager's messages for the node.
6. For ancestor discovery, suppress descriptors already represented by an applicable executable nearer the starting
   node. Sort by kind, then ancestor distance, then description for the same node.

Always preserve the returned node alongside the executable. Its description and execution must use that node, even
when discovery started on a descendant. `getAllIntentionFactories()` is a catalog for settings/presentation; it does
not perform node applicability checks or instantiate parameter variants.

The manager is not the whole Alt+Enter menu: `IntentionMenuProducer` separately adds IntelliJ actions from the
actions-as-intentions groups. Conversely, the manager's result can contain migration refactorings and diagnostic
quick fixes in addition to language intention definitions.

Source: `editor/intentions-runtime/source/jetbrains/mps/intentions/IntentionsManager.java` and
`IntentionsVisitor.java`; menu composition in
`editor/intentions-runtime/source/jetbrains/mps/editor/intentions/IntentionMenuProducer.java`.

## Identity: definition versus executable choice

Use `executable.getDescriptor().getPersistentStateKey()` as the intention definition's runtime ID. This is the key
MPS uses for enable/disable state and `getIntentionsById` lookup. For generated intentions extending
`AbstractIntentionDescriptor`, it is the factory's fully qualified Java class name, for example:

```text
jetbrains.mps.baseLanguage.intentions.AlterStatementListContainer_Intention
```

Treat the value as opaque. Quick fixes use a serialized predicate instead of a class name; migrations use their
refactoring class name. Class-based keys are not guaranteed stable across intention or language renames.

`getIntentionNodeReference()` points to the defining model node where supplied. It is useful for navigation and
definition metadata, but is nullable and does not identify a parameter variant. `getPresentation()` names the
definition without context; `getDescription(node, context)` labels the concrete executable choice. Neither text
method promises uniqueness or stability.

`getIntentionsById(node, context, id)` calls ordinary discovery with `currentNodeOnly=true`, then matches the
persistent state key. It returns **all matching executable instances**, including disabled ones, and excludes
surround-with intentions because those other query flags retain their defaults. Use explicit discovery and filter
its returned pairs when different query semantics are required. There is no manager method that executes an ID.

Source: `AbstractIntentionDescriptor.java`, `QuickFixAdapter.java`, `MigrationRefactoringAdapter.java`, and
`IntentionsManager.java` in `editor/intentions-runtime/source/jetbrains/mps/intentions/`.

## Parameterized intentions

An intention factory computes the available parameters and creates one executable per parameter. The value is
already bound into the executable; `execute` does not take an additional parameter argument. This is also the
model described in the [MPS intentions documentation](https://www.jetbrains.com/help/mps/mps-intentions.html).

```java
if (executable instanceof ParameterizedIntentionExecutable parameterized) {
  Object parameter = parameterized.getParameter();
}
```

Concrete source examples:

- `languages/baseLanguage/baseLanguage/source_gen/jetbrains/mps/baseLanguage/intentions/AlterStatementListContainer_Intention.java`:
  parameters are `SAbstractConcept` objects for the replacement statement kinds. Instances share the enclosing
  factory descriptor and have descriptions such as `Change to ... statement`.
- `languages/languageDesign/generator/languages/templateLanguage/source_gen/jetbrains/mps/lang/generator/intentions/AddPropertyMacroParam_property_Intention.java`:
  parameters are `SNode` property declarations. Parameter discovery also reads the selected editor cell.

The public interface provides no generic parameter serializer, parameter schema, variant ID, or factory method to
recreate an executable from an arbitrary supplied parameter. Implementations can expose arbitrary objects. Neither
object equality nor `toString()` has a universal identity contract. All variants may also have the same Java
executable class, so the class name cannot distinguish them.

For an external API, a recommended design is to give each discovered `(executable, applicable node, editor context)`
choice an opaque, short-lived token. Return the definition key, description, target node reference, and optional
parameter metadata alongside it. Invalidate selections after relevant model/editor changes, language reload, or
editor disposal; re-discover rather than execute stale choices. This is an integration recommendation, not a token
facility provided by MPS.

For repeatable requests without retaining instances, match the definition key and a deliberately supported semantic
parameter representation against freshly discovered candidates, requiring exactly one match. Examples include node
references for node parameters and concept identities for concept parameters. Unsupported parameter types require
an adapter or a fresh selection token. Descriptions and list indices are not durable selectors. Matching a key alone
is safe only when discovery produces exactly one candidate for the intended target and context.

## Execution and editor context

The operation itself is:

```java
executable.execute(applicableNode, editorContext);
```

The standard editor wraps it in an `EditorCommand` and submits that through
`repository.getModelAccess().executeCommandInEDT(...)`. A minimal source-derived equivalent is:

```java
editorContext.getRepository().getModelAccess().executeCommandInEDT(
    new EditorCommand(editorContext) {
      @Override
      protected void doExecute() {
        executable.execute(applicableNode, editorContext);
      }
    });
```

`jetbrains.mps.editor.runtime.commands.EditorCommand` ships in `lib/mps-editor-runtime.jar`. Its `run()` brackets
execution with editor command-started/finished callbacks. The menu's private `IntentionCommand` additionally
implements `UndoRunnable` to name the undo entry and customizes virtual-file node handling. `execute()` itself does
not acquire a command, re-discover applicability, or save the model. Run discovery and selection against the current
state before executing; calling `isApplicable` alone does not repeat factory/concept/language filtering or regenerate
parameter choices.

Source: `editor/intentions-runtime/source/jetbrains/mps/editor/intentions/IntentionMenuProducer.java` and
`editor/editorlang-runtime/source/jetbrains/mps/editor/runtime/commands/EditorCommand.java`.

The editor menu documents discovery as running on EDT with model read access. The manager casts
`editorContext.getEditorComponent()` to `jetbrains.mps.nodeEditor.EditorComponent`, obtains its typechecking session,
and uses `TypecheckingFacade.computeWithSession`. Quick-fix discovery also reads that component's highlight manager.
The menu skips discovery when its typechecking session is absent. A null context or arbitrary lightweight context
implementation is therefore not a substitute for the concrete editor infrastructure.

Availability is not solely a function of `SNode`: selection, selected cell, typechecking state, and editor diagnostics
can affect the result. Execution can move selection or invoke other editor behavior. A node-only interface must
define which editor selection/context it establishes; it cannot promise exact equivalence to an arbitrary caret
position in an open editor.

`HeadlessEditorComponent` is a candidate for supplying the concrete editor infrastructure; see
[editor-cell-rendering.md](editor-cell-rendering.md). Its verified rendering behavior does not verify intention
discovery or execution. A runtime probe must establish session availability, discovery, parameterized execution,
selection effects, command/undo behavior, and disposal in the intended host. Diagnostic quick-fix completeness also
requires populated editor messages; the manager does not run the editor highlighter to obtain them on demand.
