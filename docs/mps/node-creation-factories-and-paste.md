# Node factories, paste post-processors, and structure meta-ids

Verified against the MPS `com.jetbrains:mps:2025.1.2` distribution jars and the MPS 2025.1 source. Source paths below
are relative to the MPS source repository root.

MPS has **two distinct** "run language-defined setup logic on a node" mechanisms. They fire on different occasions and
have different effects; confusing them is easy and dangerous.

## 1. Node factories — fired when a *fresh* node is created in the editor

A node factory fills in a blank, newly created node (typing a concept, pressing Enter for a mandatory child, the
"create node" completion action). It never fires on copy/paste.

### API — `jetbrains.mps.smodel.action.NodeFactoryManager` (`mps-editor.jar`)

Source: `editor/actions-runtime/source/jetbrains/mps/smodel/action/NodeFactoryManager.java`.

```
static SNode createNode(SAbstractConcept nodeConcept, SNode sampleNode, SNode enclosingNode, SModel model)
static SNode createNode(SAbstractConcept nodeConcept, int index, SNode enclosingNode, SContainmentLink link, SModel model)
static void  setupNode (SAbstractConcept nodeConcept, SNode node, SNode sampleNode, SNode enclosingNode, SModel model)
static void  setupNode (SAbstractConcept nodeConcept, SNode node, SNode sampleNode, int index, SNode enclosingNode, SContainmentLink link, SModel model)
```

- `createNode` creates the node, runs `setupNode`, then recursively creates mandatory (non-optional) children.
- `setupNode` is the runnable-on-an-existing-node entry point: it walks the concept's ancestors
  (`DepthFirstConceptIterator`), for each fetches the language runtime's `ActionAspectDescriptor`
  (`mps-editor-api.jar`), and calls every `NodeFactory.setup(node, sampleNode, enclosingNode, index, model)` for that
  concept. `NodeFactory` is `jetbrains.mps.openapi.actions.descriptor.NodeFactory`.

### Why you must NOT call `setupNode` to "fix up" a copy

Factories are written assuming a blank node and can clobber real data. Concrete example from
`jetbrains.mps.lang.structure.actions` (`languages/languageDesign/structure/languageModels/actions.mps`): the
`STRL_node_factories` factory for `ConceptDeclaration` **unconditionally sets `extends` to `BaseConcept`**. Running it
over a copied concept would silently reset that concept's superconcept. A separate `SetStructureIds` factory (same
model) assigns `conceptId`/`propertyId`/`linkId`/`datatypeId`/`memberId` — but the sibling factory riding along is the
hazard.

## 2. Paste post-processors — fired when a node is *pasted* (this is the "copy" behavior)

When MPS pastes a copied subtree, `jetbrains.mps.nodeEditor.datatransfer.NodePaster` (`mps-editor.jar`) deep-copies
with fresh node ids and then runs paste post-processing. This is the mechanism that reassigns a duplicated
`ConceptDeclaration`'s `conceptId` — i.e. what "the way the MPS IDE does it on copy" refers to.

### Facade — `jetbrains.mps.datatransfer.DataTransferManager` (`mps-editor.jar`, platform classpath)

Source: `editor/editing-runtime/source_gen/jetbrains/mps/datatransfer/DataTransferManager.java`.

```
static DataTransferManager getInstance()
void postProcessNode(SNode pastedNode)     // recursive; run inside a write action
```

`postProcessNode(node)` (~line 98):

1. Looks up a `PastePostProcessor` (`jetbrains.mps.openapi.actions.descriptor.PastePostProcessor`, interface
   `getApplicableConcept()` / `postProcessNode(SNode)`) by the node's **exact concept** — not a superconcept walk. The
   cache is keyed by each processor's `getApplicableConcept()` and populated from every loaded language's
   `ActionAspectDescriptor.getPastePostProcessors()`.
2. If one matches: run it and **return without recursing** — the processor owns its own subtree traversal.
3. If none matches: call `NodeIdentityComponent.getInstance().configure(node, model, null)` (assigns node-identity for
   identity-carrying concepts) and recurse into all children.

This is generic: it faithfully reproduces IDE paste post-processing for **any** concept, without the caller knowing
about the structure language. `DataTransferManager` is a singleton `LanguageRegistryListener` that builds its cache
from loaded language runtimes, so the relevant languages must be loaded.

### What the structure language's paste post-processor does

`jetbrains.mps.lang.structure.actions` registers a `StructureIds` `CopyPasteHandlers` with `PastePostProcessor`s for
`ConceptDeclaration`, `InterfaceConceptDeclaration`, `PropertyDeclaration`, `LinkDeclaration`, `DataTypeDeclaration`,
and `EnumerationMemberDeclaration`. Each delegates to
`jetbrains.mps.lang.structure.util.ConceptIdSetter` (source:
`languages/languageDesign/structure/source_gen/jetbrains/mps/lang/structure/util/ConceptIdSetter.java`):

```
static void processConcept(SNode root, SModel m, boolean force)   // force = true on paste
static void processProperty(SNode prop, SNode root, boolean force)
static void processLink(SNode link, SNode root, boolean force)
static void processDatatype(SNode root, SModel m)
static void processEnumMember(SNode member, SNode root)
```

`processConcept(root, m, true)` reassigns `conceptId`, then recurses into `propertyDeclaration` and `linkDeclaration`
children reassigning `propertyId`/`linkId`. With `force=false` it only fills empty ids.

The id value comes from `ConceptIdHelper` (same package):

```
static long generateConceptId(SModel m, SNode c)   // and generateDatatypeId/PropertyId/LinkId/EnumMemberId
```

Contract (source: `.../util/ConceptIdHelper.java`): the default id is **the node's own regular node id** as a `long`
(`getDefaultIdFromNode`). If another declaration *in the same model* already carries that id as its meta-id, it falls
back to `randomLong()` (`(long)(Math.random() * Long.MAX_VALUE)`) and retries until unique. So the IDE convention
`conceptId == declaration node id` holds except on a same-model collision, where the id is random. Uniqueness is
checked only within the current model's roots (for concepts/datatypes) or within the owning declaration's children
(for properties/links/members), not globally.

- `ConceptIdSetter` and `ConceptIdHelper` ship in the **language module** jar
  `languages/languageDesign/jetbrains.mps.lang.structure.jar`, i.e. loaded by MPS's module classloader — **not** on the
  platform classpath. Call them only reflectively through the module classloader. Prefer the `DataTransferManager`
  facade, which reaches them through the aspect descriptors without the caller needing the language on its classpath.

## Gotchas

- **Copy ≠ node factory.** Reproduce copy/paste behavior with `DataTransferManager.postProcessNode`, not
  `NodeFactoryManager`. Node factories assume a blank node and can overwrite copied data (e.g. the `extends` reset
  above).
- **Exact-concept match for post-processors.** A `PastePostProcessor` is found only by the pasted node's exact
  concept, so a language must register one for each concrete concept it wants handled (the structure language does).
- **Write action + loaded languages.** `postProcessNode` mutates the model and must run inside an MPS write action;
  its cache requires the owning languages to be loaded. These are runtime contracts — verify by executing, not by
  reading signatures.
- **Effects are silent property rewrites.** Post-processing changes meta-id *properties* (conceptId/propertyId/linkId)
  but not *node ids*, so anything addressing the subtree by node id or role path is unaffected; anything that recorded
  the old meta-id values is now stale.
