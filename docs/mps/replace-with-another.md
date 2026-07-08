# Replacing a node in place: SNodeUtil.replaceWithAnother

Verified against the MPS 2025.1.2 distribution jars and the MPS 2025.1 source. Source paths below are relative to the
MPS source repository root.

## The API

```
org.jetbrains.mps.openapi.model.SNodeUtil.replaceWithAnother(@NotNull SNode node, SNode replacer): SNode
```

- Ships in `mps-openapi.jar`.
- Source: `core/openapi/source/org/jetbrains/mps/openapi/model/SNodeUtil.java` (~line 76).
- Returns `replacer`.

## Deletion is detachment

There is no destroy step in the MPS model API. `SNode.delete()`
(`core/kernel/source/jetbrains/mps/smodel/SNode.java` ~line 219) is exactly `parent.removeChild(this)` for a child or
`model.removeRootNode(this)` for a root. A detached subtree is an ordinary object graph: it stays alive while the
caller holds a reference to it — its nodes can still be read, moved, or re-attached — and is garbage-collected when
dropped. "Delete the remainder" therefore means "detach and drop", never a required cleanup call.

## Behavior contract

**Child case** (`node.getParent() != null`):

1. Captures `node.getNextSibling()` as the anchor.
2. Detaches `node` from its parent; the subtree stays alive while referenced.
3. Detaches `replacer` from its current parent if it has one — `replacer` may come from anywhere, including from
   inside the just-detached subtree of `node`, which makes single-call unwrap work.
4. Inserts `replacer` before the anchor in `node`'s containment link: **the exact sibling position is preserved**.

**Root case** (`node.getParent() == null` and `node.getModel() != null`):

1. `node.delete()` — i.e. `removeRootNode`; the old root's subtree is detached but alive while referenced.
2. `model.addRootNode(replacer)`. `addRootNode` (`core/kernel/source/jetbrains/mps/smodel/SModel.java` ~line 196)
   itself detaches a parented `replacer` — including one sitting inside the old root's detached subtree — and also
   handles a `replacer` that is currently a root of another model. Single-call root unwrap works too.

If `node` is parentless and model-less, nothing happens (`replacer` is still returned). **`replacer == null`** turns
the child case into plain removal of `node`.

## Gotchas

- **No attribute migration.** Annotations (`smodelAttribute` children) on the old node stay on it and go wherever its
  subtree goes. The smodel-level wrapper
  `jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations.replaceWithAnother` (`mps-core.jar`,
  `core/kernel/smodelRuntime/source_gen/.../SNodeOperations.java`) adds only a null assertion — no attribute copy.
- **`replaceWithNewChild` is the attribute-copying sibling.** `SNodeOperations.replaceWithNewChild(node, concept)`
  creates a fresh node of `concept`, swaps via the openapi method, then copies the old node's attributes onto the new
  node, skipping (and logging an error for) property/link attributes whose property or link does not exist on the new
  concept. Reach for it only when attribute migration is actually wanted.
- **Detached nodes are addressable only while you hold them.** Once detached, a node is no longer reachable through
  the model (lookups by model + node id stop finding it); only live references into the subtree keep it usable.
- **No constraint or cardinality checking.** The swap is raw model surgery; validity is the caller's concern.
