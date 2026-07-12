# Detecting a stale module: is a model's generated output outdated w.r.t. its sources?

**Question this answers:** given a module (a language, generator, or solution) that has been built, how do you detect
— headless — that its generated output and therefore its compiled runtime are **outdated** with respect to the module's
model sources on disk? I.e. the "built but stale" case: the `.mps` sources changed (or were reverted) after the runtime
was generated, so name lookup through the compiled runtime can return a concept whose identity contradicts the current
sources.

**Answer:** `jetbrains.mps.generator.ModelGenerationStatusManager.generationRequired(SModel)` returns `true` exactly
when a model needs regeneration — its current content differs from what was recorded at its last generation. A module is
stale iff any of its generatable models reports `generationRequired`. This is the same detection MPS's own command-line
make uses to skip unmodified models, so it works headless.

Verified against MPS **2025.1.2** (`com.jetbrains:mps:2025.1.2`), cross-read in the **MPS2025.1** source checkout.
Source paths below are relative to the [JetBrains/MPS](https://github.com/JetBrains/MPS) repository root.

## The API

```
jetbrains.mps.generator.ModelGenerationStatusManager            // mps-generator.jar
  boolean generationRequired(SModel md)
  Collection<SModel> getModifiedModels(Collection<? extends SModel> models)   // batch: the subset that generationRequired
```

Obtain it as a **project component**, the same way MPS's headless make worker does:

```kotlin
val mgsm = project.getComponent(ModelGenerationStatusManager::class.java)
```

`jetbrains.mps.tool.builder.make.BaseGeneratorWorker` (`core/tool/builder/source_gen/.../BaseGeneratorWorker.java`
~:120) — the ant / command-line generation worker — obtains it exactly this way and calls `getModifiedModels(...)` to
implement its "skip unmodified models" option. That worker runs headless, so this path is confirmed headless-safe. The
class is a `CoreComponent`; `getComponent` is the supported accessor (there is no `getInstance()`).

## What `generationRequired` compares

Source: `core/generator/source/jetbrains/mps/generator/ModelGenerationStatusManager.java` (~:150). The logic, in order:

```java
public boolean generationRequired(SModel md) {
  if (!(md instanceof GeneratableSModel)) return false;          // not a generatable model
  GeneratableSModel sm = (GeneratableSModel) md;
  if (!sm.isGeneratable()) return false;                         // generation disabled for it
  if (sm instanceof EditableSModel && ((EditableSModel) sm).isChanged()) return true;  // unsaved in-memory edits
  String currentHash = currentHash(sm);                         // actual current content
  if (currentHash == null) return true;                         // can't tell -> assume stale
  String generatedHash = getLastKnownHash(sm);                  // hash recorded at last generation
  return !currentHash.equals(generatedHash);
}
```

So `true` means one of: it has unsaved edits, its current content hash cannot be computed, or **its current content hash
differs from the hash stored when it was last generated**. The last is the stale-runtime case this note is about.

### `currentHash` — the actual current content (reads the file)

`currentHash(sm)` first asks `ModelDigestHelper.getModelHash(md)`
(`core/persistence/source/jetbrains/mps/persistence/ModelDigestHelper.java`), which consults registered `DigestProvider`s
(an IDEA workspace-index lookup). **Headless there is normally no such provider**, so it returns `null` and
`generationRequired` falls back to `md.getModelHash()` on the model itself.

`GeneratableSModel.getModelHash()` (impl in
`core/persistence/source/jetbrains/mps/persistence/LazyLoadFacility.java` ~:68) is
`ModelDigestUtil.hash(getSource(), textBased)` — it hashes the model's **`DataSource`** (the `.mps` file's stream)
directly, with the in-source comment "hash value representing actual model content (i.e. no cached values)". It reads
the current file bytes, so `currentHash` reflects **on-disk source state** (after a VFS refresh; see
`headless-vfs-refresh-and-reload.md`), not a cached digest. This is precisely "outdated w.r.t. sources on disk".

### `getLastKnownHash` — the hash recorded at last generation (reads the `generated` cache file)

`getLastKnownHash` reads `GenerationDependenciesCache.get(sm).getModelHash()`
(`core/generator/source/jetbrains/mps/generator/impl/dependencies/GenerationDependenciesCache.java`). That is parsed
from a per-model cache file named **`generated`**, located under the model's generation-output cache directory
(`GenerationTargetFacet.getOutputCacheLocation(model)` — i.e. alongside `source_gen`). It holds the model hash captured
the last time the model was generated. In the field-test scenario — sources reverted, `source_gen`/caches **not**
cleaned — this file still holds the *pre-revert* (attempt-1) hash, so it differs from the reverted `currentHash` and
`generationRequired` returns `true`. After `mops make`, generation rewrites this file with the new hash and the two
match again.

## From a module to its staleness

A module is stale iff any of its generatable models needs regeneration. Enumerate with `SModule.getModels()` and filter,
inside a read action — again mirroring `BaseGeneratorWorker.collectResources`:

```kotlin
project.modelAccess.runReadAction {
  val mgsm = project.getComponent(ModelGenerationStatusManager::class.java)
  val staleModels = mgsm.getModifiedModels(module.models.toList())   // == models.filter { mgsm.generationRequired(it) }
  val outdated = staleModels.isNotEmpty()
}
```

For a language, its structure/behavior/etc. models are all in its module; any of them being stale means the compiled
runtime may not match the sources. `getModifiedModels` is just `models.filter(::generationRequired)` collected into a
`LinkedHashSet`, so use whichever reads better.

## Gotchas

- **Read action required.** Every call reads the repository / model sources.
- **The last-known-hash cache is memoized in memory, keyed by model reference.** `BaseModelCache.get`
  (`core/generator/source/jetbrains/mps/generator/cache/BaseModelCache.java`) parses the `generated` file **once** and
  caches the parsed `GenerationDependencies`; it does not re-stat the file on later calls. It is invalidated by
  `ModelGenerationStatusManager`'s repository listener on model reload / replace / add / remove
  (`invalidateData`), by `invalidateData(IFile)` for external file-change notifications, and it is refreshed by a make
  (generation calls `update(...)` with the new dependencies). So a `generationRequired` result reflects the on-disk
  `generated` file as of the first query or the last invalidation — fine for "detect stale at startup, refuse, and let a
  `make` clear it", but do not expect it to notice a `generated` file rewritten by an *external* process without one of
  those invalidations.
- **`currentHash`, by contrast, is recomputed from the data source each call** (no memoization in the headless fallback),
  so the *current* side always reflects the live source; only the *last-generated* side is cached as above.
- **Null cache location reads as stale.** If a model has generatable content but no readable `generated` file
  (`getLastKnownHash` returns `null`) while `currentHash` is non-null, `generationRequired` returns `true`. A
  never-generated model thus reads as "needs generation", which is correct for the unbuilt case but means the signal
  alone does not distinguish "unbuilt" from "built-but-stale" — pair it with the runtime-loaded check
  (`LanguageRegistry.withModuleRuntime`, see `language-runtime-loading.md`) if you need to tell those apart.
- **Non-generatable and read-only models report `false`.** Deployed/packaged modules (loaded from jars, no editable
  source) are not `GeneratableSModel` / not generatable, so they never report stale — appropriate, since you cannot
  edit or regenerate them anyway.

## Re-verify triggers

- On an MPS upgrade, re-read `ModelGenerationStatusManager.generationRequired` — the three-way hash logic and the
  headless fallback from `ModelDigestHelper.getModelHash` to `GeneratableSModel.getModelHash()` are the load-bearing
  parts.
- If `getComponent(ModelGenerationStatusManager.class)` ever returns null headless, check that the generator
  platform component is registered in the daemon's environment (it is a `CoreComponent`, initialised by
  `MPSGenerator`).
- The `generated` cache-file name and its `GenerationTargetFacet` location are stable back to 2022; re-check if
  generation output layout changes.
