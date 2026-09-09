# Java parsing, reference resolution, and automatic ambiguity fixes

Verified public signatures against `com.jetbrains:mps:2025.1.2` and `com.jetbrains:mps:2026.1`
distribution. Behavior was read from generated Java sources in JetBrains/MPS:

- 2025.1: [f4d90532](https://github.com/JetBrains/MPS/tree/f4d90532bcac5e0339b3161cec38abf49567cffb).
- 2026.1: [abde4a3b](https://github.com/JetBrains/MPS/tree/abde4a3becb006eff0e39a9ac1094f064cb4eb22).
- master: [1af2e619](https://github.com/JetBrains/MPS/tree/1af2e619c24d1fe8db2ed6cb7e722edc365e13b1).

The parser package and generated Java platform package are identical between the latter two revisions.
The comparison applies to these specific revisions.

The empty ambiguity-pass input described below was additionally confirmed in the 2025.1.2 bytecode. No runtime probe
was performed: threading, typechecking-session setup, classloader availability in an embedding application, and
headless execution remain unverified. Public signatures alone do not establish these contracts.

## Entry points and packaging

The contextual paste actions convert textual Java into BaseLanguage nodes; the user-facing operation is documented
in [Base Language: Import textual Java](https://www.jetbrains.com/help/mps/base-language.html).

| Class/package | Distribution jar | Purpose |
| --- | --- | --- |
| `jetbrains.mps.java.core.newparser.JavaParser` | `plugins/mps-java/lib/java-core.jar` | Java text to detached nodes |
| `jetbrains.mps.java.core.newparser.JavaToMpsConverter` | Same | Ordered reference resolution and code transforms |
| `jetbrains.mps.java.core.newparser.YetUnknownResolver` | Same | Repeated substitution of ambiguous concepts |
| `jetbrains.mps.java.platform.util.JavaPaster` | `plugins/mps-java/lib/java-platform.jar` | Clipboard, UI, insertion, and resolution orchestration |
| `jetbrains.mps.baseLanguage.behavior.*`, `jetbrains.mps.baseLanguage.typesystem.*` | `languages/baseLanguage/jetbrains.mps.baseLanguage.jar` | Concept-specific resolution and quick fixes |

The IntelliJ plugin id is `jetbrains.mps.ide.java` (MPS Java Integration). The solution
`65557aaa-5381-435d-b705-3f8d546e0f40(jetbrains.mps.java.core)` declares externally provided classes in `java-core.jar`
and dependencies on BaseLanguage, JavaDoc, closures, method references, and Eclipse.ECJ. These jars are not all in
`lib/`; an embedding application must establish visibility through the appropriate plugin/module loading setup.

All signatures below use `org.jetbrains.mps.openapi.model.SNode` / `SModel`,
`org.jetbrains.mps.openapi.module.SRepository`, and `org.jetbrains.mps.openapi.util.ProgressMonitor`.

```java
// jetbrains.mps.java.core.newparser.JavaParser
public JavaParser();
@NotNull public JavaParseResult parseCompilationUnit(String code) throws JavaParseException;
@NotNull public JavaParseResult parse(String code, FeatureKind what, SNode context,
                                      boolean recovery) throws JavaParseException;
public static void tryResolveDynamicRefs(Iterable<SNode> nodes);

// JavaParser.JavaParseResult
@NotNull public List<SNode> getNodes();
public Set<SLanguage> getLanguages();
public String getPackage();
public String getErrorMsg();

// jetbrains.mps.java.core.newparser.JavaToMpsConverter
public JavaToMpsConverter(SModel model, SRepository repository, IMessageHandler messageHandler);
public void tryResolveRefs(Iterable<SNode> nodes, FeatureKind level, ProgressMonitor progress);

// jetbrains.mps.java.core.newparser.YetUnknownResolver
public YetUnknownResolver(SModel model);
public YetUnknownResolver(SModel model, Iterable<SNode> nodes);
public void tryResolveUnknowns(ProgressMonitor progress);
public boolean collectYetUnresolved(ProgressMonitor progress);
public void replaceYetUnresolved(ProgressMonitor progress);
public void updateWithImportsOfResolved();
public void withImportsOfResolved(Consumer<SModelReference> callback);
```

The result's package, error, and languages can be null; only `getNodes()` is declared non-null. The parser's context
parameter has no declared nullability; the implementation accepts null for compilation units and statements.
`IMessageHandler` is `jetbrains.mps.messages.IMessageHandler`; `SLanguage` is in the OpenAPI language package.

## Parsing and insertion

`JavaParser` uses Eclipse JDT `CodeSnippetParsingUtil`, followed by `FullASTConverter` for executable code.
The source level is hardcoded to `CompilerOptions.VERSION_1_8`, including in 2026.1/master. A newer MPS or JDK
does not make this entry point a parser for modern Java syntax.

| FeatureKind | Input and output | Paste action |
| --- | --- | --- |
| `CLASS` | Compilation unit; zero or more classifier roots | `PasteAsJavaClass_Action` |
| `CLASS_CONTENT` | Class body declarations, including fields, constructors, methods, and nested types; pass the destination classifier as context | `PasteAsJavaMethods_Action` |
| `STATEMENTS` | Statement sequence; returns individual statements, not a StatementList | `PasteAsJavaStatements_Action` |
| `CLASS_STUB` | Compilation unit using the stubs converter | No equivalent among these actions |

`FIELD`, `METHOD`, and `NESTED_CLASS` exist in the enum but fall through to `IllegalArgumentException` in `parse`.
There is no expression parsing mode. `parseCompilationUnit(code)` delegates to `parse(code, CLASS, null, true)`.
The recovery flag is forwarded for class contents and statements; the compilation-unit branch does not use it.

Results are detached. Insertion supplies the model and surrounding scope needed for name resolution. `JavaPaster`
adds classes as roots, members into `Classifier.member`, and statements into `StatementList.statement`. Anchored
member/statement insertion is before the anchor, with append when no applicable child anchor exists.
It adds reported languages and then invokes `JavaToMpsConverter.tryResolveRefs` on the attached nodes.

The UI wrapper rejects text longer than 50,000 characters, uses dialogs/message views, and schedules commands via
IntelliJ application APIs. That limit is not enforced by `JavaParser`. For a programmatic integration, the parser
and resolver are the useful boundary; copying the UI wrapper also copies clipboard and event-loop dependencies.

Inspect both `getNodes()` and `getErrorMsg()`: recovery can return nodes with problems. Diagnostics are coarse:
`problemDescription` returns only `"There were some problems"` when JDT reports problems. Some failures return the
`UNKNOWN_ERROR` result, and conversion can throw `JavaParseException`. The paste UI does not inspect the error
string when nonempty nodes are returned. Parsing success does not establish semantic validity or fidelity.

Languages are additional-language information, not a guaranteed inventory of every concept in the output. In
2025.1, in particular, JavaDoc is not added to this set at its construction sites, and child converters do not
propagate all collected languages back to their parent. Importing the languages actually present in the attached
tree avoids relying exclusively on this set.

## Model imports and module dependencies

In the inspected 2025.1 source, recording a model import does not establish the corresponding module dependency.
`ModelImports.addModelImport` delegates to the model's import mutation. `ModelValidator.validate` reports an error
when an imported model resolves in the repository but is not visible in the importing module's scope.

`ModelDependencyUpdate` separates `updateImportedModels(repository)` from `updateModuleDependencies(repository)`.
The latter checks imported models against the owning module's scope and adds a non-reexported dependency on an
imported model's module when necessary. Already visible models need no additional dependency. The public
`JavaToMpsConverter.tryResolveRefs` path calls `updateUsedLanguages().updateImportedModels(null)`, not
`updateModuleDependencies`. `YetUnknownResolver.updateWithImportsOfResolved` and `JavaParser.tryResolveDynamicRefs`
also add imports without establishing module dependencies.

`updateModuleDependencies` examines all model imports, even when the helper was constructed with a subtree list.
Calling it can therefore repair dependencies for unrelated existing imports. It supports `AbstractModule` owners
and skips imports whose owning module cannot be determined. Dependency completeness must not be inferred solely
from the absence of unresolved nodes or references.

Source locations in JetBrains/MPS:

- `core/smodel/source/jetbrains/mps/smodel/ModelImports.java`.
- `core/kernel/source/jetbrains/mps/smodel/ModelDependencyUpdate.java`.
- `core/project-check/source/jetbrains/mps/project/validation/ModelValidator.java`.
- `JavaToMpsConverter.java`, `YetUnknownResolver.java`, and `JavaParser.java` in the parser source directory listed below.

## What the ambiguous nodes mean

The parser creates concepts implementing `jetbrains.mps.baseLanguage.structure.IYetUnresolved`. Examples include
`UnknownNameRef`, `UnknownDotCall`, `UnknownInstanceMethodCall`, `UnknownNew`, and `UnknownConsCall`; method references
also have `jetbrains.mps.baseLanguage.methodReferences.structure.UnknownMethodReference`.

These represent unresolved structural choices, not just missing reference targets. For example, the tokens in
`a.b.C.f` could start with a variable or denote a classifier followed by a static field. `ResolveUnknownUtil` tries
the first token in variable scope, then classifier prefixes from longest to shortest, and builds the corresponding
variable/static-field/enum/dot-expression structure. A qualified call may first become `UnknownInstanceMethodCall`,
which needs another pass once the receiver has a type.

The common behavior method `IYetUnresolved.evaluateSubst` returns a nullable
`_FunctionTypes._return_P0_E0<? extends SNode>`: null means no replacement is currently available; otherwise invoking
the returned builder constructs the replacement. Builders may move arguments/children out of the original node.
Instance-call resolution consults `TypecheckingFacade`. This is MPS scope/type-based conversion, not full JDT binding
resolution. Some selection is heuristic: `UnknownInstanceMethodCall.getResolvedMethod` selects a visible method by
name, and the constructor helper falls back to argument count when multiple constructors exist. Check the resulting
model rather than treating substitution as proof of correct Java overload resolution.

`YetUnknownResolver.tryResolveUnknowns` performs up to 100 passes:

1. Traverse the supplied subtrees for `IYetUnresolved`, evaluate substitutions, and invoke available builders.
2. Replace old nodes with the newly built nodes.
3. Add model imports from replacement static references and languages used by replacement subtrees.
4. Repeat while at least one substitution was found; descend into unresolved children when their parent cannot
   yet be substituted.

Stopping means no substitutions are available, or the watchdog expired; it does not mean every unknown was resolved.
The method returns void. Count remaining `IYetUnresolved` nodes and inspect unresolved references separately.
The constructor accepting a node iterable can target members/statements as well as roots; the model-only constructor
targets all roots. Prefer the explicit subtree overload to limit mutation.

Despite its name, `collectYetUnresolved` is mutating because it invokes builders. Despite the convenience method's
Javadoc mentioning model-access wrapping, its current body does not establish a write action or command. The public
converter method likewise uses `IncrementalModelAccess.INSIDE_COMMAND_OR_UPDATE_MODE`, whose methods directly run
their callbacks. The caller must provide mutation access; the exact supported thread/session setup needs execution
verification.

## Reference resolution and the paste-path gap

`JavaToMpsConverter.tryResolveRefs` runs ordered passes for superclass/interfaces, field/method types, ambiguity
substitution, variable types, variables, dot operands/operations, static classifiers/members, and remaining references.
It then transforms array `length`/`clone`, enum references, and certain static accesses; removes unneeded Java import
attributes; and updates used-language/model imports with `ModelDependencyUpdate`.

Dynamic references that find a target are replaced with references carrying the target's identity and resolve text.
Unresolved dynamic references remain. The converter keeps a `myVisitedRefs` set, including references it could not
resolve, so use a fresh converter for a deliberate subsequent pass.

**The public paste path does not actually supply its nodes to the ambiguity pass.** A fresh
`JavaToMpsConverter(model, repository, handler)` initializes `myAttachedRoots` to an empty list. Public
`tryResolveRefs(nodes, ...)` initializes `myModels`, but does not populate that list. Internally it constructs:

```java
new YetUnknownResolver(model, roots(model).intersect(myAttachedRoots))
```

Consequently, that pass processes no nodes for this usage. The file-conversion entry point `convertToMps` populates
`myAttachedRoots` and does not have this particular problem. The behavior is present in the 2025.1.2 binary and
unchanged in the inspected 2026.1/master source.

For explicit post-insertion processing, a candidate sequence is: run the converter's reference passes; invoke
`new YetUnknownResolver(model, insertedSubtrees).tryResolveUnknowns(progress)`; then run a fresh converter to resolve
references introduced by replacements and perform cleanup. Repeat only with bounded progress detection if another
round is necessary. This composition is a source-derived integration recommendation, not an executed recipe.
Use current attached subtree anchors if a supplied node itself can be replaced.

`JavaParser.tryResolveDynamicRefs` is a smaller traversal that fixes dynamic references with available targets and
adds target model imports. It neither substitutes `IYetUnresolved` nor performs the converter's code transforms.

Java imports are scope metadata. In 2025.1 the parser adds a `JavaImports` attribute including an on-demand entry for
the source package. The converter retains all Java import entries while any `IYetUnresolved` remains; with no unknowns,
it retains entries needed by dynamic references, and removes all when everything is resolved. Do not strip these
attributes before resolution. Java text imports do not themselves make absent library modules available. The
file-conversion entry point adds a JDK module dependency; the public paste-oriented resolver does not do that step.

## Why the editor fixes nodes later

BaseLanguage non-typesystem rules such as `check_UnknownNameRef_NonTypesystemRule` call the same `evaluateSubst`
behavior. If a replacement is available, the rule reports a diagnostic with a
`BaseQuickFixProvider("jetbrains.mps.baseLanguage.typesystem.ResolvedUnknownNode_QuickFix", ..., true)`.
The final `true` marks the fix for immediate execution. If no replacement exists, the rule reports an unresolved error.

`ResolvedUnknownNode_QuickFix.execute(SNode)` re-evaluates the builder, invokes it, and calls
`SNodeOperations.replaceWithAnother`. The unknown node is supplied through the quick fix's `unknownNode` argument.
It is not a general subtree resolver and does not itself perform the batch resolver's import-update step.

`AbstractTypesystemEditorChecker` selects auto-applicable fixes when enabled and schedules their execution with
`ApplicationManager.invokeLater` inside an undo-transparent command, checking that the fix is still alive.
This is an editor checking/highlighting mechanism. Merely parsing, saving, or requesting a model check should not
be relied upon to execute it. An explicit `YetUnknownResolver` call provides the same concept-specific substitution
behavior without depending on that editor scheduler.

## Extending AST-to-BaseLanguage conversion

The converters offer usable Java override points, but `JavaParser` does not expose converter injection. Its `parse`
method directly constructs `new FullASTConverter(null)` or `new ASTConverterWithExpressions(stubsMode)`. There is no
converter constructor parameter, factory method, or converter registration hook in this path. Subclassing the
converter alone therefore does not alter `JavaParser.parse`. A custom parsing wrapper must select the converter and
reproduce the relevant parsing, comment attachment, import annotation, and result assembly steps.

The class hierarchy is `ASTConverter` → `ASTConverterWithExpressions` → `FullASTConverter`, all in
`jetbrains.mps.java.core.newparser` and shipped in `plugins/mps-java/lib/java-core.jar`. They are public and non-final.
Useful public/protected signatures, verified in both 2025.1.2 and 2026.1 binaries, include:

```java
// ASTConverter
public SNode convertRoot(ASTNode node) throws JavaParseException;
public List<SNode> convertClassContents(ASTNode[] astNodes, SNode container) throws JavaParseException;
public SNode convertTypeReference(TypeReference type);
protected ASTConverter withNewState(ASTConverter.State state);
protected ASTConverter.State getState();
public Set<SLanguage> getAdditionalLanguages();
public Map<Integer, SNode> getJavadocs();

// FullASTConverter
public FullASTConverter(CompilationUnitDeclaration cud);
public SNode convertExpression(Expression expression) throws JavaParseException;
public SNode convertStatement(Statement statement) throws JavaParseException;
public SNode convertStatementWrap(Statement statement) throws JavaParseException;
public void convertStatementsInto(AbstractMethodDeclaration method, SNode statementList)
    throws JavaParseException;
protected FullASTConverter withNewState(ASTConverter.State state);
public Map<SNode, Integer> getPositions();
public Iterable<FullASTConverter.CodeBlock> getCodeBlocks();
```

AST parameter types are from `org.eclipse.jdt.internal.compiler.ast`. These signatures do not declare nullability.
An external-package subclass overriding the generic expression, statement, and type methods, plus `withNewState`
and `getState`, compiled against both distributions (using Java 21 for the 2026.1 binary). This confirms Java access
and override compatibility; it does not validate conversion behavior or comment preservation at runtime.

### Dispatch and state limitations

- Override the generic `convertExpression(Expression)` or `convertStatement(Statement)` to intercept selected AST
  kinds and delegate other cases to `super`. Concrete overloads such as `convertExpression(MessageSend)` and
  `convertStatement(ForeachStatement)` have package access, so an external-package subclass cannot override them.
  Dispatch inside the base class still reaches those base overloads. Some internal calls also use concrete overloads
  directly, so the generic methods are not a universal interception point for every internal conversion call.
- `convertExpressionWrap` invokes the generic expression method and restores parentheses. `convertStatementWrap`
  invokes the generic statement method and records source positions. Preserve these wrapper behaviors when adding
  conversions that must retain parentheses or participate in comment placement.
- `convertTypeVars` creates child converters for generic classes and methods through `withTypeVarNames` and
  `withTypeVarDecls`, which call `withNewState`. The inherited implementation returns a private
  `FullASTConverterWithState`, not the custom subclass. Custom conversion would consequently disappear inside those
  scopes unless the subclass also overrides `withNewState` to return its own converter and `getState` to retain the
  supplied state. Stub conversion also creates state for identifier prefixes.
- `FullASTConverter(FullASTConverter base)` is private. An external subclass cannot reuse this copy constructor;
  using the public constructor creates fresh JavaDoc, language, position, and code-block collections. Scope state
  alone does not preserve this metadata. The base `ASTConverter` copy constructor shares the JavaDoc map, but the
  full converter's private constructor prevents using that copying path from a subclass. A custom implementation
  needs explicit metadata aggregation/sharing as well as state propagation, with tests for generic methods and
  nested declarations containing comments. Public getters expose metadata for inspection/aggregation, but private
  conversion code writes directly to internal collections; overriding getters alone does not redirect those writes.
- `ASTConverter.usedLanguages` changes from protected in 2025.1.2 to private final in 2026.1. Add extension languages
  through the mutable set returned by `getAdditionalLanguages()` instead of accessing the field.

These limitations are established by `JavaParser`, `ASTConverter.convertTypeVars`, the `withNewState` implementations,
and the constructors and dispatchers in `ASTConverterWithExpressions` and `FullASTConverter`. Their source paths are
in the parser package listed in the source map. The `FullASTConverter` implementation is unchanged apart from generated
annotation metadata between the inspected 2025.1 and 2026.1 sources; the 2026.1/master package comparison also applies.

### Choosing an extension strategy

For syntax-driven extensions, a custom parsing wrapper around the MPS-bundled `CodeSnippetParsingUtil`, with a
`FullASTConverter` subclass supplying selected conversions, is a feasible starting point. It reuses existing
conversion logic but requires ownership of state propagation and metadata handling. It is not a ready-made converter
plugin API. If extensions need pervasive interception of private/package-access helpers, a maintained converter fork
or upstream changes to the factory, visibility, and copying contracts would provide more control.

The class named `org.eclipse.jdt.internal.core.util.CodeSnippetParsingUtil` is itself present in `java-core.jar`, with
generated source under
`plugins/mps-java/core/modules/jetbrains.mps.java.core/source_gen/org/eclipse/jdt/internal/core/util/CodeSnippetParsingUtil.java`.
Using this MPS-bundled implementation preserves its matching JDT AST types. Substituting an independently selected
Eclipse version requires compatibility verification; the package name does not establish binary compatibility.

For transformations that depend on what a Java call means, conversion after BaseLanguage reference/type resolution
is a separate option. For example, recognizing a collection operation by its resolved receiver and method is safer
than recognizing an unresolved method name. The stock converter emits BaseLanguage `ForeachStatement` for Java
enhanced-for syntax and ordinary/unknown call constructs for method calls; these paths contain no collections-language
conversion registry. An explicit subsequent transformation pass can introduce collections-language constructs, but
must preserve semantics and report unresolved cases. If the stock pipeline cannot resolve a construct in the intended
language context, such a pass cannot assume that resolution has already succeeded.

For an integration intended to grow AST conversion support, keep parser orchestration, converter selection, and
post-resolution transformations as separate boundaries. Prototype subclass state/metadata handling before committing
to that approach; a complete replacement converter is not required merely to add a few AST cases.

## Changes in 2026.1 and the inspected master

| Area | Difference from 2025.1.2 / 2025.1 source |
| --- | --- |
| Core API | Unchanged |
| Syntax | Unchanged |
| Import helper | Public `annotateWithmports(...)` is renamed to `annotateWithImports(...)`; direct callers need updating |
| Package scope | The helper creates `JavaImports` only for explicit imports and no longer inserts the compilation unit's package as an on-demand import |
| JavaDoc output | Uses `jetbrains.mps.lang.text` lines, structured block tags and inline tags/references rather than the older plain CommentLine representation |
| Language accounting | Adds JavaDoc language at construction sites and propagates child-converter language collections in class conversion |
| Converter subclass API | `ASTConverter.usedLanguages` becomes private final; use `getAdditionalLanguages()` |
| Ambiguity resolver | Before adding used languages, includes languages exported by imported DevKits in its effective-language set, avoiding redundant direct imports |
| Paste wrapper | `pasteJavaAsNode` gains a final `boolean isJavadocComment`; the old seven-argument signature is absent; adds `pasteJavaDoc(...)` and two JavaDoc actions |
| Ambiguity semantics | Unchanged |

The JavaDoc paste wrapper normalizes comment delimiters and appends a dummy class or method before invoking the
existing parser. It does not introduce a new JavaDoc FeatureKind. Generated `trim_*` helpers are implementation
artifacts, not useful integration entry points.

For callers that insert a compilation unit into a model with a different package name, explicitly test same-package
name resolution on 2026.1: the source package no longer accompanies the nodes as an implicit import entry.

## Source map and verification follow-up

Paths below are relative to the JetBrains/MPS repository at the commits above:

- Parser, converter, result, enum, and batch resolver:
  `plugins/mps-java/core/modules/jetbrains.mps.java.core/source_gen/jetbrains/mps/java/core/newparser/`.
- Paste orchestration:
  `plugins/mps-java/platform/modules/jetbrains.mps.java.platform/source_gen/jetbrains/mps/java/platform/util/JavaPaster.java`;
  sibling `actions/PasteAsJava*_Action.java` files select the modes.
- Concept behavior:
  `languages/baseLanguage/baseLanguage/source_gen/jetbrains/mps/baseLanguage/behavior/ResolveUnknownUtil.java`,
  `IYetUnresolved__BehaviorDescriptor.java`, and `Unknown*__BehaviorDescriptor.java`.
- Editor fixes:
  `languages/baseLanguage/baseLanguage/source_gen/jetbrains/mps/baseLanguage/typesystem/check_Unknown*_NonTypesystemRule.java`
  and `ResolvedUnknownNode_QuickFix.java`.
- Classifier scope:
  `languages/baseLanguage/baseLanguage.scopes/source_gen/jetbrains/mps/baseLanguage/scopes/ClassifiersScope.java`.
- Immediate-fix scheduling:
  `editor/typesystemIntegration/source/jetbrains/mps/typesystem/checking/AbstractTypesystemEditorChecker.java`.

A runtime integration probe should cover all three insertion modes, qualified static/instance access, constructors,
overloads, nested unknowns, lambdas/method references, missing libraries, malformed input, and same-package imports.
Run with assertions enabled, verify the access/typechecking setup, count remaining unknown/dynamic references, run
model checks, and save/reload the result. In particular, verify that explicit resolution completes without opening an
editor. None of those runtime outcomes is established by this source/signature investigation.
