# Why a concept name resolves, and diagnosing when it doesn't

Verified against the MPS 2025.1.2 distribution jars (the daemon's compile/runtime target) and the MPS 2025.1 source,
and cross-checked against `MPS/MPS` master (build 261, 2026.1). Source paths below are relative to an MPS source
checkout root.

## The invariant: name lookup only sees loaded language runtimes

`find instances` resolves its concept name through
`jetbrains.mps.smodel.language.ConceptRegistry.getConceptByName(String)` (mps-core.jar,
`core/kernel/source/jetbrains/mps/smodel/language/ConceptRegistry.java` ~:160). That method builds a name→concept
cache by iterating **only the language runtimes registered in the language registry**:

```java
// 2025.1
for (SLanguage l : myLanguageRegistry.getAllLanguages())
    for (SAbstractConcept c : l.getConcepts())
        cache.put(c.getQualifiedName(), c);
return cache.getOrDefault(conceptName, new InvalidConcept(conceptName));
```

Master (2026.1) rewrites the loop to `myLanguageRegistry.withAvailableLanguages(lr -> { for (c : lr.getConcepts()) … })`
— it iterates `LanguageRuntime.getConcepts()` instead of `SLanguage.getConcepts()`, but the **invariant is identical**:
a concept is resolvable by name only if its owning language's runtime is registered/available. An unknown name yields
an `InvalidConcept` whose `isValid()` is false. This is exactly the `CONCEPT_NOT_FOUND` path in
`JetBrainsMpsAccess.findInstances`. `getConceptByName` is `@Deprecated` but remains the only by-name lookup (name
lookup is inherently a legacy-persistence need); there is no non-deprecated replacement.

Consequence: "the name is wrong" and "the language is not compiled" are not the only causes. The real gate is **is the
language's runtime registered in this environment**. A language can be present on disk, correctly named, and compiled,
yet still be unregistered because a module it depends on is missing — and then its concepts are invisible to name
lookup.

## Ground truth: is a module's runtime loaded?

`jetbrains.mps.smodel.language.LanguageRegistry` (mps-core.jar, `core/kernel/.../LanguageRegistry.java`). Obtain via
`project.getComponent(LanguageRegistry.class)` (the `getInstance()` overloads are deprecated).

- **`void withModuleRuntime(Stream<SModuleReference> modules, Consumer<ModuleRuntime> op)` (~:780; since 2023.3,
  present in master)** — maps each reference through the deployed-runtime map (`myModuleRuntime`) and invokes `op` only
  for modules that actually have a registered runtime. Passing a single module reference and recording whether `op`
  fired is the **per-module "loaded" test that works for any module kind** (languages, solutions, generators), not just
  languages. This is the per-module load check a diagnostic can build on. (It's an awkward API — a `forEach`-style
  probe rather than a boolean — but it is public and stable.)
- `@Nullable LanguageRuntime getLanguage(SLanguage)` (~:442) — the language-specific equivalent; `getLanguage(L) != null`
  means loaded. Internally a language whose runtime class fails to load is put in `myLanguagesNoRuntime` and gets no
  entry.
- `Collection<SLanguage> getAllLanguages()` (~:435) — snapshot of the registered (loaded) languages only.

A language registers only when `LanguageRegistry.createRuntime(Language)` (~:188) succeeds at
`getClassLoader(l).loadOwnClass(l.getModuleName() + ".Language")`. On `ClassNotFoundException`/`NoSuchMethodException`
it logs `"Missing language runtime class for module %s (make failed?); returning null"` and the language is skipped —
this single catch swallows a missing-dependency failure.

`ClassLoaderManager.getStatus(SModule): DeploymentStatus` (~:520, public: `DEPLOYED` / `NOT_DEPLOYED` / `NOT_IN_REPO`)
is a coarser public alternative, but it returns `DEPLOYED` as soon as a classloader exists — even for a module whose
classes were never built (empty CL) — so it cannot tell "loaded" from "built but empty". `withModuleRuntime` reflects
actual runtime registration and is preferred.

## Enumerating the languages a project uses

- `jetbrains.mps.project.Project.getProjectModulesWithGenerators()` (mps-core.jar,
  `core/project/.../Project.java` ~:123) — the project's modules. `getProjectModules(Class)` (~:150) filters by type
  but excludes generators.
- `org.jetbrains.mps.openapi.module.SModule.getUsedLanguages(): Set<SLanguage>` (mps-openapi.jar,
  `core/openapi/.../SModule.java` ~:100) — the languages a module's models import. Union across project modules gives
  the languages the project's own code refers to. Note this does **not** include a language the project *defines* but
  whose concepts no project model instantiates (e.g. a language whose only models are its own aspects). Add those from
  `project.getProjectModules(jetbrains.mps.smodel.Language.class)`, mapping each to its identity with
  `jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory.getLanguage(SModuleReference)` (mps-core.jar; the
  `getLanguage(SModuleReference)` overload — verified in this package in both 2025.1.2 and master). "Every language in
  the project" = used ∪ defined.
- `jetbrains.mps.smodel.ModuleRepositoryFacade.getAllModules(Class)` (mps-core.jar) — e.g.
  `getAllModules(Language.class)` for every loaded language in the whole repository, when project scope is too narrow.

## Classifying why a module is not loaded

An unloaded module (one for which `withModuleRuntime` did not fire) can be classified into a stable
reason, built from the signals below rather than MPS's version-volatile classloading graph. The kinds:

- `ABSENT` — the module reference does not resolve in the repository.
- `NOT_A_MODULE` — resolves, but not to a `jetbrains.mps.project.AbstractModule` (only those load classes).
- `NO_JAVA_FACET` — no `JavaModuleFacet`. For a `jetbrains.mps.smodel.Language` this is a defect (a language must ship
  classes) and blocks its dependents; for any other module it is legitimate (it may generate XML/text/etc.) and is
  reported without blocking dependents.
- `CLASSES_DISABLED` — has a facet, but `getLoadClasses().classesAvailable()` is false (the facet is configured not to
  load classes into MPS).
- `NOT_BUILT` — the facet expects MPS-managed classes, but they are not there yet: `getClassesGen()` (the source
  module's design-time class output) is absent or empty. A deployed module has `getClassesGen() == null` and loads from
  the classpath, so it counts as built.
- `BROKEN_DEPENDENCIES` — the module itself is fit to load but a dependency is not loaded; the analysis recurses into
  the dependency (which may itself be `ABSENT`, `NOT_BUILT`, …), so the leaves of the tree are the root modules to fix.
- `RUNTIME_LOAD_FAILED` — classes, facet, and all dependencies check out, yet the runtime still did not register;
  usually a class-link or version error, visible in the daemon log as the `createRuntime` message above.

The dependency closure walked for `BROKEN_DEPENDENCIES` is: declared dependencies + used languages + (for a language)
its runtime solution modules. The signals:

- `org.jetbrains.mps.openapi.language.SLanguage.getSourceModule(): SModule?` (mps-openapi.jar,
  `core/openapi/.../SLanguage.java` ~:58) — **null exactly when the language's own module is absent from the
  repository.** (`SLanguageAdapterById.getSourceModule` resolves `ModuleId.regular(idValue)` against the repository and
  returns null if unresolved.)
- `SModule.getDeclaredDependencies(): Iterable<SDependency>` (mps-openapi.jar, `SModule.java` ~:95).
- `org.jetbrains.mps.openapi.module.SDependency` (mps-openapi.jar, `core/openapi/.../SDependency.java`):
  `@NotNull SModuleReference getTargetModule()` (~:36, the declared identity — never null) and
  **`@Nullable SModule getTarget()` (~:44) — null when the dependency cannot be resolved (target module absent).**
  `getTargetModule()` names it even when unresolved.
- `jetbrains.mps.smodel.Language.getRuntimeModulesReferences(): Collection<SModuleReference>` (mps-core.jar,
  `core/kernel/.../Language.java` ~:158) — a language's runtime solution modules. These are part of the classloading
  closure but are **not** in `getDeclaredDependencies()`, so a diagnostic that ignores them misses a common cause: an
  absent runtime solution. Resolve each with `SModuleReference.resolve(repository)`; null means absent.

The dependency analysis walks this closure. A declared dependency's target is absent when
`SDependency.getTarget() == null`; a present target is recursed into. The walk memoizes each module's result and guards
cycles, so it is bounded by the number of distinct modules reachable.

## Facet signals for the compiled/built check

- `jetbrains.mps.project.facets.JavaModuleFacet` (mps-core.jar, `core/project/.../facets/JavaModuleFacet.java`), via
  `module.getFacet(JavaModuleFacet.class)` (null ⇒ no Java facet — the `NO_JAVA_FACET` case): `getLoadClasses()`
  (`classesAvailable()` false ⇒ `CLASSES_DISABLED`) and `getClassesGen(): IFile?` (design-time class output; null for a
  deployed module, otherwise checked for existence and non-empty children to tell built from `NOT_BUILT`).
- `jetbrains.mps.project.SModuleOperations.classesAvailableToMPS(SModule)` (~:141) `= facet != null &&
  facet.getLoadClasses().classesAvailable()` is MPS's own predicate for "participates in classloading"; the analyzer
  splits it into the `NO_JAVA_FACET` and `CLASSES_DISABLED` cases instead of collapsing both to "not compiled".

## Gotchas

- **`getClassLoader` hides the reason.** `ClassLoaderManager.getClassLoader(module)`
  (`core/kernel/.../ClassLoaderManager.java` ~:280) returns a **system-delegating fallback loader** when the module's
  classloading status is invalid (its guard is `ModulesWatcher.getStatus(ref).isValid()`). So reproducing
  `createRuntime`'s `loadOwnClass("<module>.Language")` on an unloaded language throws a generic
  `ClassNotFoundException` that does **not** name the missing dependency. Walk the dependency closure instead (above)
  to name it.
- **Dependency cycles are legal and are not a load failure.** Module dependency cycles are normal in MPS (a language
  and its runtime solution commonly depend on each other). `ModulesWatcher.refillStatusMap`
  (`core/kernel/.../ModulesWatcher.java` ~:142, :174) explicitly tolerates them: a module whose only unresolved
  dependencies are cycle members, with no genuinely broken dependency, is marked `LIKELY_VALID`/`VALID` (loadable). So
  a diagnostic should treat a back-edge to a module already on the current path as a non-blocking edge that
  contributes no cause; a module blocked only by a cycle is deferred rather than reported, and its real root cause
  surfaces when a cycle member is diagnosed as a top-level module (whose path starts empty).
- **A read action is required.** Every call here touches the repository; run inside `project.modelAccess`
  read access.
- **Cost.** The `withModuleRuntime` check is cheap; the per-module dependency walk that explains an unloaded module is
  memoized and bounded by the number of distinct modules reachable, so a full project scan stays fast enough for
  an on-demand command even on a large project.

## Why we avoid MPS's internal classloading graph

The richest data lives in `jetbrains.mps.classloading.ModulesWatcher` (per-module `ClassLoadingStatus`:
`VALID` / `INVALID_DEPENDENCIES` / `INVALID_NOT_LOADABLE` / `ERROR`; it produces the log line `"<module>: not tracked
for classloading (no respective module facet or absent in a repository)"`). But obtaining the watcher goes through
`ClassLoaderManager.getModulesWatcher()`, which is `@TestOnly` package-private, and `CLDependencies` (the real
classpath-closure computation) is a package-private class. This is precisely the area that churns between MPS versions,
so a robust diagnostic deliberately reconstructs the needed facts from the stable openapi/registry surface above
rather than depending on those internals. When re-verifying for a new MPS version, the registry and openapi contracts
here are the ones to re-check.
