# Rendering a node's default editor as plain text, headlessly

Verified against the MPS 2025.1.2 distribution jars (`~/.mps-platform-cache/mps/2025.1.2/lib`) and the MPS 2025.1
source checkout (`~/Projects/MPS/MPS2025.1`). Source paths below are relative to that checkout.

## TL;DR — use the shipped path

MPS already ships a headless editor component and a one-call utility that does exactly this. Do **not** hand-roll an
`EditorComponent`, an `EditorContext`, or a Swing parent. Two levels:

- **`jetbrains.mps.nodeEditor.text.NodeRenderUtil.renderNodeAsSingleLine(SNode, SRepository)`** — one call, but returns
  `null` unless the node renders to exactly one line. Only useful for inline single-line rendering.
- **`jetbrains.mps.editor.runtime.HeadlessEditorComponent`** — the general path. Build it, `editNode`, read
  `getRootCell().renderText().getText()`, `dispose`. This is what `NodeRenderUtil` itself does internally.

The recipe is `NodeRenderUtil.renderNodeAsText` (a `private` method), promoted to multi-line:
Source: `editor/editor-runtime/source/jetbrains/mps/nodeEditor/text/NodeRenderUtil.java:38` (MPS 2025.1).

```java
// repository = classicMpsProject.getRepository()   (org.jetbrains.mps.openapi.module.SRepository)
// node       = any SNode that is registered in a model (see gotchas: not necessarily a root)
// Runs on the EDT under a read action: renderText only needs the read, but dispose() asserts the EDT (see threading).
String[] out = new String[1];
repository.getModelAccess().runReadInEDT(() -> {
  HeadlessEditorComponent comp = new HeadlessEditorComponent(repository);
  try {
    comp.editNode(node);                     // builds the cell tree synchronously, takes its own read action
    out[0] = comp.getRootCell()              // EditorCell root of the default editor
                 .renderText()               // -> jetbrains.mps.openapi.editor.TextBuilder
                 .getText();                 // -> String, lines joined with '\n'
  } finally {
    comp.dispose();                          // required, and must run on the EDT — see gotchas
  }
});
```

`NodeRenderUtil` itself does the build/dispose without the EDT wrapper (it runs on the EDT already, e.g. for tooltips);
off the EDT the wrapper above is needed so `dispose()` does not trip its EDT assertion.

That is the whole recipe. Everything below is the confirmation and the edge cases.

## The classes and signatures

### `HeadlessEditorComponent`
- Jar: **`mps-editor.jar`** (`jetbrains/mps/editor/runtime/HeadlessEditorComponent.class`).
- Source: `editor/editor-runtime/source_gen/jetbrains/mps/editor/runtime/HeadlessEditorComponent.java` (generated from
  the `jetbrains.mps.editor.runtime` model).
- Constructors (both `public`):
  - `HeadlessEditorComponent(SRepository repository)` — the one to use.
  - `HeadlessEditorComponent(SNode node, SRepository repository)` — **`@Deprecated`**; it calls `editNode` from the
    constructor, so an exception thrown while building cells escapes before you hold a reference to `dispose()`. Avoid.
- It extends the concrete `jetbrains.mps.nodeEditor.EditorComponent` and overrides three things to make it headless
  (`HeadlessEditorComponent.java:38-52`):
  - `attachListeners()` / `detachListeners()` → empty (no Swing/model listeners wired up).
  - `assertInEDT()` → **empty**. On the base `EditorComponent`, `assertInEDT()` asserts `ThreadUtils.isInEDT()`
    (`EditorComponent.java:2101`); the headless subclass drops it, so cell **building** (`editNode`/`renderText`) may run
    off the EDT. This does **not** make `dispose()` EDT-free: `dispose()` reaches `NodeHighlightManager.dispose()`, whose
    own `assert ThreadUtils.isInEDT()` (`NodeHighlightManager.java:345`) is not overridden — see the threading section.
- Construction internally passes `new EditorConfigurationBuilder().withUI(false).build()` to the base constructor, so
  `hasUI()` is false and all the Swing/scrollpane/highlighter branches are skipped.

### `EditorComponent.editNode(SNode)` (the base class, inherited)
- Jar: **`mps-editor.jar`** (`jetbrains/mps/nodeEditor/EditorComponent.class`).
- Signature: `public synchronized void editNode(org.jetbrains.mps.openapi.model.SNode)`.
- Source: `editor/editor-runtime/source/jetbrains/mps/nodeEditor/EditorComponent.java:1203`.
- Behavior contract (confirmed from source):
  - **Wraps its whole body in `getModelAccess().runReadAction(...)`** (`EditorComponent.java:1213`). So the caller does
    **not** need to already hold a read action — `editNode` acquires it. (It is safe to call from inside an existing
    read action too; MPS read actions are reentrant.)
  - Asserts `node.getModel() != null` — "Can't edit a node that is not registered in a model" (`:1215`).
  - Asserts `SNodeUtil.isAccessible(node, myRepository)` — the node must belong to the **same repository** you passed to
    the constructor (`:1216`).
  - Calls `rebuildEditorIgnoreViewport()` (`:1247`), which is `getUpdater().update(); relayout();`
    (`EditorComponent.java:2066`). `getUpdater().update()` builds the cell tree **synchronously**. So after `editNode`
    returns, `getRootCell()` holds the real cells — there is no separate flush to call.

### `getRootCell()`
- `public EditorCell getRootCell()` — returns `jetbrains.mps.openapi.editor.cells.EditorCell`
  (`EditorComponent.java:1802`). Before `editNode`, the field is a placeholder empty
  `EditorCell_Constant` (`:367`); after `editNode` it is the node's editor root. Always call `editNode` first.

### `EditorCell.renderText()` → `TextBuilder`
- Jar: **`mps-editor-api.jar`** (`jetbrains/mps/openapi/editor/cells/EditorCell.class`).
- Signature: `jetbrains.mps.openapi.editor.TextBuilder renderText()`.
- `TextBuilder` is also in **`mps-editor-api.jar`**. `TextBuilder.getText()` returns the rendered text with lines
  joined by `'\n'` (`editor/editorlang-runtime/source/jetbrains/mps/editor/runtime/TextBuilderImpl.java:42`).
  `TextBuilder.getSize()` returns the line count; `getLines()` iterates the raw lines.

### `NodeRenderUtil` (the single-line convenience)
- Jar: **`mps-editor.jar`** (`jetbrains/mps/nodeEditor/text/NodeRenderUtil.class`).
- `public static String renderNodeAsSingleLine(SNode node, SRepository repository)` — returns the text if it is exactly
  one line, else **`null`** (`NodeRenderUtil.java:30`). The multi-line `renderNodeAsText` next to it is `private`, which
  is why you inline its three lines rather than call it.

## How text is reconstructed from the cell tree (whitespace + indentation)

`renderText()` is dispatched per cell type and is **entirely driven by the logical cell tree and its style attributes —
it does NOT read cell geometry (x/y/width) or font metrics.** This is why it is safe headless.

- **Leaf label cells** (`EditorCell_Label`, and its subclasses `EditorCell_Constant`, `EditorCell_Property`,
  `EditorCell_RefPresentation`, …): `renderText()` returns `new TextBuilderImpl(getRenderedText())`
  (`cells/EditorCell_Label.java:906`). `getRenderedText()` is `getRenderedTextLine().getText()` (`:135`), i.e. the
  cell's displayed string — the real text, or the grey "null text" placeholder when the cell has no text set
  (`getRenderedTextLine()`, `:481`). **Use `renderText()`, not `getText()`**: `getText()` (`:127`) returns only the
  literally-set text and skips the null-text fallback.
- **Non-label leaf cells** (`EditorCell_Basic` base, e.g. images/components): `renderText()` returns an **empty**
  `TextBuilder` (`cells/EditorCell_Basic.java:790`). They contribute nothing to the text.
- **Collection cells** (`EditorCell_Collection`): `renderText()` delegates to `myCellLayout.doLayoutText(this)`
  (`cells/EditorCell_Collection.java:702`). The layout decides the whitespace:

  | Layout class (`nodeEditor/cellLayout/`) | `doLayoutText` behavior | Source |
  |---|---|---|
  | `CellLayout_Vertical` | Each child on its own line: `result.appendToTheBottom(child.renderText())` → children joined by `\n`. | `CellLayout_Vertical.java:202` |
  | `CellLayout_Horizontal` | Children concatenated left-to-right: `appendToTheRight(child.renderText(), PunctuationUtil.hasLeftGap(child))`. A single space is inserted before a child iff `hasLeftGap` is true (punctuation/style driven). | `CellLayout_Horizontal.java:138` |
  | `CellLayout_Indent` | Reconstructs newlines **and** indentation from style flags (see below). | `CellLayout_Indent.java:184` |
  | `CellLayout_Flow`, `CellLayout_Table`, `CellLayout_Superscript` | Have their own `doLayoutText`; not audited line-by-line here. Flow behaves like horizontal-with-wrapping; Table lays rows/cols. If you hit a language that uses these, re-verify. | — |

  `appendToTheRight`/`appendToTheBottom` are the multi-line-aware string join primitives in `TextBuilderImpl`
  (right = merge line-by-line with an optional single-space delimiter; bottom = stack blocks). They pad shorter lines
  with spaces to keep a rectangular block, so horizontally-joined multi-line cells stay aligned.

### Indent layout (the important one — most block languages use it)
`CellLayout_Indent.doLayoutText` (`CellLayout_Indent.java:184-218`) walks the flattened "indent leaves" of the
collection and, for each leaf, consults **style attributes** (never geometry):

- Newline **before** a leaf: `isOnNewLine(root, cell)` — true if the cell (or an ancestor up to the collection whose
  first cell it is) carries `StyleAttributes.INDENT_LAYOUT_ON_NEW_LINE` (`:69`).
- Newline **after** a leaf: `isNewLineAfter(root, cell)` — true for `INDENT_LAYOUT_NEW_LINE` on the cell, or
  `INDENT_LAYOUT_CHILDREN_NEWLINE` on its parent collection (`:99`).
- Indent depth of a new line: `getIndent(root, cell)` counts ancestors carrying `INDENT_LAYOUT_INDENT` (`:81`). One
  unit of indent = one `EditorCell_Indent.getIndentText()`, which is `EditorSettings.getInstance().getIndentSize()`
  spaces — **default 2 spaces per level** (`cells/EditorCell_Indent.java:37`).
- On each new line it emits `"\n"` then `getIndent` copies of the indent text, then the leaf's own `renderText()`
  (`:202-210`).

So faithful newlines + indentation come out of `renderText()` for free — you do not reconstruct them yourself. The
wrapping/overflow logic in the same file (`CellLayouter`, `haveToSplit`, `myMaxWidth`) is **only** used by the geometry
`doLayout`, not by `doLayoutText`; text rendering has no line-width wrapping.

### Existing utility to prefer
`jetbrains.mps.nodeEditor.text.TextRenderUtil` (`text/TextRenderUtil.java`, mps-editor.jar) serializes a **selection**
to text and is the model for how MPS itself does cell→text (it is the "copy as text" path). For rendering a whole node
you don't need a selection — `getRootCell().renderText()` is the direct equivalent. No other/better serializer exists;
`renderText()` **is** the canonical one (`TextRenderUtil`'s javadoc points at `EditorCell#renderText()`).

## The reflective (default) editor

When a concept has no editor, MPS does **not** fail — `EditorCellFactoryImpl.getCachedEditor` returns null and MPS
builds the generic **reflective editor** via `AbstractDefaultEditor.createEditor`
(`editor/editor-runtime/source/jetbrains/mps/nodeEditor/cells/EditorCellFactoryImpl.java:99,113`). It renders the
concept's alias followed by each role and its children (e.g. `json object { contents : ... }`). This same fallback is
used both when a concept legitimately defines no editor and when the concept did not resolve at all, so the reflective
output on its own does not distinguish the two.

## Concept validity: `SConcept.isValid()`

`org.jetbrains.mps.openapi.language.SAbstractConcept.isValid()` (`mps-openapi.jar`) is false when a node's concept did
not resolve — typically because its owning language's runtime is not loaded (for a source language, not compiled). MPS
still builds an editor for such a node (the reflective/error editor), so `isValid()` is the signal for "the concept did
not resolve", independent of what the editor produced.

- `SLanguage` (`mps-openapi.jar`) has **no** `isValid()`; validity is observed at the concept via `SConcept.isValid()`.
- `SConcept.getLanguage().getQualifiedName()` returns the owning language's name and is recoverable from the model's
  language registry even when that language is not loaded.

## Threading / environment contract

- **Read action:** required. `editNode` takes one for you via `getModelAccess().runReadAction(...)`, and `renderText()`
  reads the already-built cell tree, so both must run under a read. Because `dispose()` also requires the EDT (below),
  the robust shape is to run build + read + dispose together in one EDT read action:
  `modelAccess.runReadInEDT(() -> { comp.editNode(n); text[0] = comp.getRootCell().renderText().getText(); comp.dispose(); })`.
- **EDT:** cell *building* does not require it — `HeadlessEditorComponent` neutralizes `assertInEDT()`, and its javadoc
  says it may be called from arbitrary threads, so `editNode`/`renderText` run off the EDT. **`dispose()`, however, does
  require the EDT** in this version: `EditorComponent.dispose()` calls `NodeHighlightManager.dispose()`, which asserts
  `ThreadUtils.isInEDT()` (`NodeHighlightManager.java:345`) — that assertion is not overridden by the headless subclass.
  Off the EDT, `dispose()` throws `AssertionError: dispose() should be called from EDT only`. So the disposal (and, to
  keep the read consistent, the whole build) must run on the EDT under a read action, e.g. via
  `ModelAccess.runReadInEDT`.
- **Headless AWT (`-Djava.awt.headless=true`):** fine. `editNode` still calls `relayout()` → geometry `doLayout`, which
  asks `EditorComponentSettings.getWidth(char,count)` for font metrics. Java's headless toolkit provides font metrics
  (via `FontRenderContext`), so geometry computes without a display; and in any case the **text** path
  (`doLayoutText`) ignores geometry entirely. `HeadlessEditorComponent` is a shipped, supported class used exactly this
  way, so headless is a supported configuration.
- **No command needed.** Rendering is read-only; you do not need `executeCommand`/write action. (`editNode` is a read
  action, not a command.)
- **Only the logical cell tree + text is needed** for the answer; geometry is computed but unused by you.

## Which jars to add to `compileOnly`

The recipe references classes from two editor jars not currently on the `compileOnly` classpath. Add:

| Jar | Classes you reference from it |
|---|---|
| `mps-editor.jar` | `jetbrains.mps.editor.runtime.HeadlessEditorComponent`; the inherited `jetbrains.mps.nodeEditor.EditorComponent` (`editNode`, `getRootCell`, `dispose`); optionally `jetbrains.mps.nodeEditor.text.NodeRenderUtil` |
| `mps-editor-api.jar` | `jetbrains.mps.openapi.editor.cells.EditorCell` (`renderText`); `jetbrains.mps.openapi.editor.TextBuilder` (`getText`/`getSize`/`getLines`) |

Both are under `~/.mps-platform-cache/mps/2025.1.2/lib/`. `SNode`/`SRepository` (`mps-openapi.jar`) and
`jetbrains.mps.project.Project` (`mps-core.jar`) are already on the classpath.

- **`mps-editor-runtime.jar`** ships `TextBuilderImpl`/`HtmlTextBuilderImpl` and is needed at **runtime**, but you don't
  name those classes in the minimal recipe, so it isn't strictly a `compileOnly` requirement. Everything is present at
  runtime anyway inside a full MPS environment (`EnvironmentKind.IDEA`) with all editor jars loaded. Adding it to
  `compileOnly` too is harmless and future-proofs against referencing the impl classes.

## Gotchas

- **Always `dispose()` in a `finally`.** `HeadlessEditorComponent` allocates a typechecking session and updater state;
  `NodeRenderUtil` disposes in `finally` for this reason. Skipping it leaks. Use the non-deprecated
  `HeadlessEditorComponent(repository)` constructor + `editNode` so `dispose()` is reachable even if `editNode` throws.
- **Node must be registered in a model.** `editNode` asserts `node.getModel() != null`. A detached/transient node will
  trip the assertion. The node does **not** have to be a root — `editNode` computes its containing root internally
  (`updateContainingRoot`), so any node in a model renders (this is how the inspector edits sub-nodes).
- **Repository must match.** The `SRepository` passed to the constructor must be the one the node lives in
  (`classicMpsProject.getRepository()`); otherwise `SNodeUtil.isAccessible` assertion fails.
- **`getText()` vs `renderText()` on labels.** For a single leaf, `EditorCell_Label.getText()` omits the null-text
  placeholder; `renderText()` includes it. For whole-node rendering you always go through `getRootCell().renderText()`,
  which uses the correct per-cell path — don't walk cells calling `getText()` yourself.
- **`editNode` may kick off a typechecking session** (`requestTypecheckingSession`, `EditorComponent.java:1264`) when an
  `Application` is present (it is, under `EnvironmentKind.IDEA`). This is background work for error highlighting and does
  not affect the returned text, but it is a reason to `dispose()` promptly.
- **`EditorSettings.getInstance()`** (used for indent width) is an application-level component; it exists under the
  IDEA environment. Indent size defaults to 2 spaces but is a user/app setting, so rendered indentation width follows
  whatever the environment has configured.
- **Don't build `EditorComponent` directly** or construct an `EditorContext`/`IdeaEditorContext` yourself — the base
  `EditorComponent` constructor and `rebuildEditorContent()` assert EDT and assume UI wiring. `HeadlessEditorComponent`
  exists precisely to bypass all of that; use it.
- **Layouts beyond Vertical/Horizontal/Indent** (Flow/Table/Superscript) have their own `doLayoutText` that were not
  audited here line-by-line. The common block/inline languages use Vertical/Horizontal/Indent, which are fully
  confirmed. If a target language uses tables or flow layout, re-verify those two files before trusting the whitespace.
