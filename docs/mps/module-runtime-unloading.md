# Why a language runtime survives closeProject (and leaks between projects)

Verified against the MPS 2025.1.2 distribution jars and the MPS 2025.1 source. Paths below are relative to an MPS
source checkout root. Companion to `language-runtime-loading.md`, which covers *why* a runtime loads; this note covers
*why it does not unload* on `closeProject`.

## The runtime lives in environment-global singletons, not in the project

A loaded module's classes and concept descriptors are held by application/environment-scoped `CoreComponent`
singletons, keyed by module identity — never by project:

- `jetbrains.mps.classloading.ClassLoaderManager` (`core/kernel/.../classloading/ClassLoaderManager.java`) is a
  `CoreComponent` (:165) constructed with **one** `SRepository` — the platform `MPSModuleRepository` (:207). It owns the
  module classloaders. Storage is `MPSClassLoadersRegistry`
  (`core/kernel/.../classloading/MPSClassLoadersRegistry.java`), a `Map<SModuleReference, ModuleClassLoader>` (:57). So a
  classloader is keyed by `SModuleReference` (module id + name), and there is exactly one such map per environment.
- `jetbrains.mps.smodel.language.LanguageRegistry` (`core/kernel/.../language/LanguageRegistry.java`) is a
  `CoreComponent` **and** a `ClassLoaderManager.DeployListener` (:83). It holds
  `Map<SModuleReference, ModuleRuntime> myModuleRuntime` (:116) and `myLanguagesById` — the loaded `LanguageRuntime`s.
  Also environment-global.
- `jetbrains.mps.smodel.language.ConceptRegistry` (`core/kernel/.../language/ConceptRegistry.java`) is a `CoreComponent`
  and a `LanguageRegistryListener` (:42); its `StructureRegistry` cache (`core/kernel/.../language/StructureRegistry.java`)
  reads descriptors from the loaded `LanguageRuntime` (below).

Consequence: `closeProject` disposes a *project*, but these singletons and their id-keyed contents outlive any single
project for the life of the JVM/environment.

## What closeProject actually does

`Environment.closeProject` reduces to `project.dispose()`:

- `EnvironmentBase.closeProject` (`core/tool/environment/.../EnvironmentBase.java` :155) → `project.dispose()`.
- For `EnvironmentKind.IDEA`, `IdeaEnvironment.closeProject`
  (`workbench/mps-platform/jetbrains.mps.ide.platform/source_gen/.../IdeaEnvironment.java` :243) routes through
  `ProjectManagerEx.closeAndDispose(ideaProject)`, which disposes the IDEA project and thereby the `MPSProject`
  component — again reaching `project.dispose()`.

`Project.dispose()` (`core/project/source/jetbrains/mps/project/Project.java` :212):

```java
public void dispose() {
  getRepository().getModelAccess().runWriteAction(() -> getProjectModules().forEach(this::removeModule));
  myRepository.dispose();
  myDisposed = true;
}
```

`removeModule` → `ProjectBase.dissociateFromProjectRepo` (`core/project/.../ProjectBase.java` :109) →
`repository.unregisterModule(module, this)`. **It unregisters the module only from the project as owner.** Symmetrically,
project modules were registered via `associateWithProjectRepo` → `registerModule(module, this)` (:101).

`MPSProject` registers/unregisters against the shared platform repository: its `ProjectRepository` wraps
`platform.findComponent(MPSModuleRepository.class)` (`workbench/mps-platform/.../MPSProject.java` :70-73). So the
project's modules always live in the one global `MPSModuleRepository`.

## The module (and its runtime) is torn down only when the last owner leaves

`MPSModuleRepository` (`core/kernel/.../smodel/MPSModuleRepository.java`) keys modules by id in
`Map<SModuleId, SModule> myIdToModuleMap` (:63) and tracks a module→owners multimap. `unregisterModule` →
`doUnregisterModule` (:241) removes the module from the repository **only if no owners remain**:

```java
myModuleToOwners.removeLink(module, owner);
boolean remove = myModuleToOwners.getByFirst(module).isEmpty();
if (remove) { ... myModules.remove(module); myIdToModuleMap.remove(id); return true; }
return false;
```

Only when it returns `true` does the repository `fireModuleRemoved(...)`. Module removal is what drives class unloading:
`ClassLoaderManager` batches repository events through `ModuleEventsHandler` (:216), and on the module-removed event
`processModuleChanges` → `unloadModules` (:392) disposes the classloader and `forgetClassLoaders` clears the id-keyed
map; `LanguageRegistry.onUnloaded` (:499) removes the `ModuleRuntime`/`LanguageRuntime` and disposes it (the
class header at ClassLoaderManager :78-100 states this contract: "When module is removed from the repository, CLManager
unloaded module's data").

Therefore: **if a module still has any owner after `closeProject`, it stays in the repository, its classloader is never
disposed, and its `LanguageRuntime` remains registered in `LanguageRegistry`.** `closeProject` removing only the project
owner is not sufficient to unload a module that any other owner still holds. Owners are `MPSModuleOwner`s; besides
`jetbrains.mps.project.Project`, `jetbrains.mps.library.SLibrary` is one (`core/project/.../library/SLibrary.java`), and
library ownership is environment-scoped, not tied to a project's lifecycle.

## Module id makes a later project reuse the already-loaded runtime

Two project directories that each contain a module with the **same module id** do not produce two independent runtimes.
`registerModule` (`MPSModuleRepository.java` :140) short-circuits on an existing id:

```java
SModule existing = getModule(moduleId);       // by SModuleId
if (existing != null) {
  ... // same-class / same-name paranoia checks (logs, does not replace)
  myModuleToOwners.addLink(existing, owner);
  return (T) existing;                          // returns the ALREADY-REGISTERED module
}
```

So if the first project's module is still registered (any surviving owner) when a second project opens, the second
project's on-disk module descriptor is **discarded**: `registerModule` returns the pre-existing `SModule` instance and
merely adds the second project as an extra owner. That pre-existing instance still points at the *first* project's
`classes_gen`, and its classloader/runtime are already loaded and cached in the environment-global registries. The
second project's own directory (which may have no compiled classes) is never consulted for that module.

## isValid() and name lookup are backed by the same loaded runtime

Both signals a caller might use flip together because both read the environment-global `LanguageRegistry` runtime:

- `SConcept.isValid()`: `SAbstractConceptAdapter.isValid()`
  (`core/kernel/.../adapter/structure/concept/SAbstractConceptAdapter.java` :254) is
  `getConceptDescriptor() != null`. For id-based concepts the descriptor comes from
  `ConceptRegistryUtil.getConceptDescriptor(SConceptId)` (`core/kernel/.../language/ConceptRegistryUtil.java` :44), which
  returns `null` when the registry yields an `IllegalConceptDescriptor` and the real descriptor otherwise. The real
  descriptor is produced by `StructureRegistry.getConceptDescriptor` (`StructureRegistry.java` :59-100) via
  `myLanguageRegistry.getLanguage(langId)` → `languageRuntime.getAspect(StructureAspectDescriptor.class)`. No loaded
  `LanguageRuntime` ⇒ `IllegalConceptDescriptor` ⇒ `isValid() == false`.
- `ConceptRegistry.getConceptByName(String)` (`ConceptRegistry.java` :160) iterates
  `myLanguageRegistry.getAllLanguages()` — the loaded runtimes only (see `language-runtime-loading.md`).

Both therefore report "valid/found" exactly when the language's runtime is registered in `LanguageRegistry`, and "not"
when it is not. When the runtime survives `closeProject` and is reused by a later same-id project, both flip to true
there even though nothing in that project was built. `StructureRegistry` additionally memoizes descriptors in
`myConceptDescriptorsById` and is cleared via its `LanguageRegistryListener` hook (`StructureRegistry.clear()` :127) when
the registry changes — so the cache does *not* mask a genuine unload; it tracks the runtime.

## Fully unloading a language runtime from a shared environment

An ordinary `closeProject` does not unload a runtime that any owner still holds (above). The supported ways to actually
drop it:

- **Remove the module from every owner.** `ModuleRepositoryFacade.unregisterModule(SModule)`
  (`core/kernel/.../smodel/ModuleRepositoryFacade.java` :307) unregisters a module from *all* its owners; once the
  owner set is empty the repository fires `moduleRemoved` and `ClassLoaderManager`/`LanguageRegistry` unload it. This
  requires knowing/holding every owner.
- **Explicit reload.** `ClassLoaderManager.reloadModules(Iterable, ProgressMonitor)` (:433) / `reload(...)` (:458)
  recreate a module's classloader (unload + load) under a write action. This is how new `classes_gen` output is picked
  up after a make; it replaces a runtime rather than leaving it stale, but it does not by itself evict a module the
  environment should forget.
- **Dispose the environment.** `ClassLoaderManager.dispose()` (:230) and `LanguageRegistry.dispose()` (:155) clear the
  singletons, but that tears down the whole shared environment, not one language.

Caveat: classloader *object* disposal is asynchronous (`MPSClassLoadersRegistry` :49) even though the id-keyed maps and
the `LanguageRegistry` runtime entries are cleared synchronously during unload.

## Which owner survives closeProject — ranked by source evidence

The scenario: open a project whose language is a plain project module (listed under `<projectModules>` in
`.mps/modules.xml`, not on any library path), run an MPS make/generate that compiles and deploys the language, then
`closeProject`. The language's runtime stays registered and resolvable for the next project. Since a module unloads only
when its **last** owner leaves (above), some owner must survive. The candidates, ruled in or out from source:

### The owner types are enumerable, which bounds the search

Everything that can own a module in `MPSModuleRepository` implements `jetbrains.mps.smodel.MPSModuleOwner`. In a
standalone IDEA environment the implementers that can own a *language* module are only:

- `jetbrains.mps.project.Project` (`core/project/.../project/Project.java`) — a project owner;
- `jetbrains.mps.library.SLibrary` (`core/project/.../library/SLibrary.java`) — a library owner, one per library path
  a `LibraryContributor` yields (`LibraryInitializer.update` :129 `new SLibrary(...)`; `SLibrary` :210
  `registerModule(instantiate(...), this)`);
- generation-transient owners: `TransientModelsProvider`'s private `new BaseMPSModuleOwner()`
  (`core/generator/.../TransientModelsProvider.java` :52) and `tempmodel/TempModuleOptions` — both for transient/temp
  *models'* modules, not for a persistent language.

(The remaining implementers are for other runtimes — the ANT builder `WorkerBase`, the MPS-in-IDEA JPS plugin, the
`build.mps` module checker — none of which is on the interactive open→make→close path.) So the surviving owner is an
`SLibrary`, another `Project`, or a leaked transient owner; each is checkable.

### 1. Used-language / library owner — REFUTED for a project-hosted language

Using a language does not register it under a new owner. A solution's used languages are declared as
`<language slang=.../>` version entries and resolved from whatever is already in the repository; there is no
`registerModule` on the "uses" path. A language that lives *inside* the project is registered exactly once, by the
project, through `ProjectBase.associateWithProjectRepo` → `registerModule(module, this)`
(`core/project/.../ProjectBase.java` :101-106); `addModule` (:126, :142) does the same. An `SLibrary` owns a module only
when the module sits on a configured library path (`LibraryInitializer`/`SLibrary` above) — the project's own module
directories are not library paths, and an empty `.mps/libraries.xml` contributes none. So for an in-project language the
project is its only owner and this candidate does not apply. (It *would* apply to a language deployed as an installed
library — that `SLibrary` owner is environment-scoped and never released by `closeProject`.)

### 2. Make/generation adding a persistent owner — REFUTED

The make path (`MakeSession`/`CoreMakeTask` under `core/make-runtime`, `GenerationSession` under `core/generator`)
registers modules with an owner in exactly one place: `TransientModelsProvider` registers transient and checkpoint
*models'* modules under its private `BaseMPSModuleOwner` (:94, :172) and unregisters them again (:73, :76). It never
adds an owner to the language or its generator. When a language exposes a generator, `Language.revalidateGenerators`
registers that generator with **the same owners as the language** (`core/kernel/.../smodel/Language.java` :231-232
`for (MPSModuleOwner o : mrf.getModuleOwners(this)) registerModule(generator, o)`) — i.e. the project, not a new owner.
Compilation + class reload (`ClassLoaderManager.reloadModules` :433) recreate classloaders but change no ownership. So
make deploys the runtime without giving the language a second, surviving owner.

### 3. Async / unflushed unload event — REFUTED as the cause of survival

Runtime eviction is synchronous with `closeProject`, not deferred:

- Module events are batched during a write action and **flushed at its end** (`ModuleEventsHandler` class doc:
  "We flush the events both at the end of write action and on request"; `refresh()` → `myDispatcher.flush()`).
- `Project.dispose` removes modules inside `runWriteAction` (:212) on `ProjectModelAccess`, which delegates to the
  global `ModelAccess` (`core/project/.../ProjectModelAccess.java`, `delegateImpl().executeCommand`), so that flush runs.
- `IdeaEnvironment.closeProject` drives disposal through `invokeAndWait(... ProjectManagerEx.closeAndDispose ...)`
  (`.../IdeaEnvironment.java` :243-253) — synchronous, so `closeProject` returns only after dispose has run.
- The flush calls `ClassLoaderManager.processModuleChanges` :536 → `unloadModules` :392 → broadcast `onUnload` →
  `LanguageRegistry.onUnloaded` :498, which takes the runtime write lock and removes the `ModuleRuntime` in-line.

So *if* the module were removed on close, `getLanguage(langId)` would already be null when `closeProject` returns. Only
the classloader **object** disposal is async (`MPSClassLoadersRegistry` :49); the id-keyed maps and the
`LanguageRegistry` runtime entry are cleared synchronously. A lagging unload therefore cannot explain a runtime that is
still *resolvable while the project is closed* — but, as the probe below shows, it is exactly this deferred classloader
retention that lets the compiled classes be **reused when the same-id module is re-registered by a later project**. See
"What the runtime probe actually shows".

### 4. Same-id `registerModule` reuse — the mechanism, not an independent cause

`registerModule` short-circuits on an existing id and **adds the new owner to the already-registered module** (:140,
quoted above). This is not a separate leak; it is the mechanism by which a module ends up with *two* owners: if any
second owner is present when a project opens, that project's same-id module folds into the existing instance as an extra
owner, and closing one project then leaves the other as owner.

### What the runtime probe actually shows

A probe settled this empirically (make a language in a project copy, close the copy, then inspect and reopen a
*different* project that contains the same-id language but has no compiled classes on disk). The measured sequence:

1. Before any make — the language's concepts are invalid (`SNode.getConcept().isValid() == false`): no runtime loaded.
2. Right after the copy closes — the language module is **absent** from `MPSModuleRepository` (`getModules()` has no
   module of that name) and **no** project-like `MPSModuleOwner` remains. The repository unload on close is genuinely
   **clean**: no surviving owner, contradicting the co-owning-`Project` hypothesis above.
3. The second project's own `classes_gen` does **not** exist on disk — it was never built.
4. Yet reopening that second project makes the language's concepts **valid**, its module `PRESENT`, and its
   `ClassLoaderManager.getStatus` **`DEPLOYED`** — with no compiled classes on that project's disk.

So the surviving thing is **not an owner and not the repository registration** (both are cleanly gone at step 2). It is
the **compiled runtime itself, retained in the environment-global classloading layer and keyed by module identity, past
the module's removal**. `closeProject` removes the module from the repository but does **not** drive that module through
the class-loading unload path, so its class-loading state stays as if loaded; when, at step 4, the same-id module
re-registers, deployment reuses that resident runtime and reports `DEPLOYED` without reading (non-existent) class files.
The classes are served from that retained state, not from the reopened project's directory.

Can the stale runtime be cleared? Measured by re-running the probe with a trigger inserted between step 2 and step 4:

- **Waiting / GC does nothing.** `System.gc()` + `runFinalization()`, and a multi-second sleep, both leave the runtime
  resident (json still valid at step 4). This is not the asynchronous classloader-*object* disposal
  (`MPSClassLoadersRegistry` :49) — that is about freeing a dead classloader's resources and is not what keeps the
  runtime *resolvable*. There is no timer or finalizer that reconciles it.
- **An explicit reload does clear it.** `ClassLoaderManager.getInstance().reloadAll(monitor)` under a write action
  (`reloadAll` :503; or `reload(Iterable<SModuleReference>, monitor)` :458 for a targeted set) reconciles the
  class-loading registry against the current repository and evicts the orphaned runtime — after it, step 4 sees json as
  **not** loaded (`isValid() == false`).

Corrected conclusion: for an in-project language, `closeProject` unregisters the module and drops its ownership cleanly
(candidates 1, 2 and 4 are not the cause here — verified: zero owners, module absent), but leaves the **deployed class
runtime** resident in the environment-global classloading layer, keyed by module id, to be reused by the next project
that registers the same id. It is not cleared by time or GC; only an explicit `ClassLoaderManager.reloadAll` /
`reload(...)` reconciles it away. Practical consequence: once any project in a JVM makes a language, every later project
that shares that language's module id sees it as loaded — regardless of whether that project was ever built — until a
reload is forced or the environment is disposed.

Probe points (via `MPSModuleRepository.getInstance()` / `ClassLoaderManager.getInstance()`):

- `getModules()` / `getModule(id)` and `getOwners(module)` right after `closeProject` — empty/absent here, proving the
  clean repository unload.
- `getStatus(module)` after the same-id module reopens — `DEPLOYED` despite no `classes_gen` on that project's disk,
  proving the classes come from the retained runtime, not the filesystem.
- The same measurement after `reloadAll(monitor)` — the module reloads from its (empty) disk and `isValid()` is now
  false, proving the reload is what evicts the stale runtime.
