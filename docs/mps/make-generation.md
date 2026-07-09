# Running generation (make) headlessly

How to run the MPS make process — model generation plus downstream compilation — on a set of modules/models from code,
without a build script, and collect the errors it reports.

Verified against the MPS 2025.1.2 distribution jars (`com.jetbrains:mps:2025.1.2`) and the
[JetBrains/MPS](https://github.com/JetBrains/MPS) source. Source paths below are relative to the MPS source repository
root.

Reference implementation: `de.itemis.mps.build-backends:execute-generators`
([mbeddr/mps-build-backends](https://github.com/mbeddr/mps-build-backends)). MPS's own headless generator worker,
`jetbrains.mps.tool.builder.make.BaseGeneratorWorker`, is a second, simpler reference.

## What "make" is

MPS generation is one facet of a broader *make* process. The make framework runs a script — an ordered set of
**targets** contributed by **facets** — over a set of input **resources** (modules with their models). The default
script goes all the way to `jetbrains.mps.make.facets.Make.make`, i.e. model-to-model generation *and* text generation
*and* Java/Kotlin compilation of the output. There is no separate "just generate" service; you run make and it does as
much as the requested facets cover.

## The pieces

| Role | Type | Jar |
| --- | --- | --- |
| Make entry point | `jetbrains.mps.tool.builder.make.BuildMakeService` | **`lib/mpsant/mps-tool.jar`** |
| Service base (make overloads) | `jetbrains.mps.make.service.AbstractMakeService` | `mps-core.jar` |
| Session parameters | `jetbrains.mps.make.MakeSession` | `mps-core.jar` |
| Input resources | `jetbrains.mps.make.resources.IResource` | `mps-core.jar` |
| Models → resources | `jetbrains.mps.smodel.resources.ModelsToResources` | `mps-core.jar` |
| Result | `jetbrains.mps.make.script.IResult` | `mps-core.jar` |
| Message sink | `jetbrains.mps.messages.IMessageHandler` / `IMessage` / `MessageKind` | `mps-core.jar` |
| Script (usually let MPS build it) | `jetbrains.mps.make.script.IScript` / `ScriptBuilder` | `mps-core.jar` |
| Controller (facet config) | `jetbrains.mps.make.script.IScriptController` | `mps-core.jar` |
| Java compile config | `jetbrains.mps.internal.make.cfg.JavaCompileFacetInitializer` | `mps-core.jar` |
| Parallel-gen settings | `jetbrains.mps.generator.GenerationSettingsProvider` | `mps-generator.jar` |

**Classpath gotcha:** the whole make framework is in `mps-core.jar`, but the concrete `BuildMakeService` ships only in
`lib/mpsant/mps-tool.jar` (the Ant/build tooling jar), *not* under `lib/` or `lib/modules/`. A classpath that globs
`lib/**/*.jar` including subdirectories (as the `execute-generators` gradle example does) picks it up; one that only
takes `lib/*.jar` + `lib/modules/*.jar` (as the mops daemon does) will not, and `BuildMakeService` is missing at
runtime.

**Reimplementing `BuildMakeService` from `mps-core` alone.** `BuildMakeService.doMake` is a thin, ~8-line wrapper over
classes that *are* in `mps-core.jar`, so a caller that can't reach `mps-tool.jar` can inline it without adding the jar:

```
// resources: Iterable<IResource> from ModelsToResources; session: MakeSession
val seq = MakeSequence(resources, /* defaultScript = */ null, session)      // jetbrains.mps.make.dependencies
val controller = IScriptController.Stub2(session)                            // BuildMakeService's own null-controller default
val task = CoreMakeTask(seq, controller, session.messageHandler)            // jetbrains.mps.make.service
task.run(EmptyProgressMonitor())                                            // synchronous
val result: IResult = task.getResult()
```

`MakeSequence`, `IScriptController.Stub2`, `CoreMakeTask`, and `IResult` all live in `mps-core.jar`. `CoreMakeTask.run`
iterates the module clusters in build order, executes each cluster's script, and **stops at the first cluster that
fails**; `getResult()` returns only the *last executed* cluster's `IResult` (not a merged one) — one more reason to
detect errors through the message handler rather than the result alone. If there are **zero clusters** (empty input, or
every input model filtered out as having no used languages) `run` leaves `myResult` as a `FAILURE("not started")`; guard
the empty-resource case *before* calling make so this sentinel is not mistaken for a real failure.

## Minimal flow

```
// 1. Collect the models to generate — needs a read action.
//    To make un-made dependencies too, expand `modules` to its dependency closure first
//    (see "Dependencies are NOT expanded automatically" below) — make() will NOT do it.
val models: List<SModel> = project.modelAccess.runReadAction(Computable {
    modules              // the requested modules, or their COMPILE-dependency closure
        .flatMap { it.models }
        .toList()
})

// 2. Turn models into make resources (grouped by module).
val resources = ModelsToResources(models).resources().toList()
if (resources.isEmpty()) return NOTHING_TO_GENERATE

// 3. Run make. Passing a null script lets MPS build the right script per module cluster.
val handler = CollectingMessageHandler()
val session = MakeSession(project, handler, /* cleanMake = */ true)
val future = BuildMakeService().make(session, resources, /* script = */ null,
                                     /* controller = */ null, EmptyProgressMonitor())
val result = future.get()

// 4. Decide success — see "IResult is not trustworthy on its own" below.
val ok = result != null && result.isSucessful && !handler.sawError
```

## `BuildMakeService.make` — signatures and behavior

Source: `core/tool/builder/source_gen/.../BuildMakeService.java`; overloads in
`core/make-runtime/.../make/service/AbstractMakeService.java`.

```
Future<IResult> make(MakeSession, Iterable<? extends IResource> resources)
Future<IResult> make(MakeSession, resources, IScript script)
Future<IResult> make(MakeSession, resources, IScript script, IScriptController controller)
Future<IResult> make(MakeSession, resources, IScript script, IScriptController controller, ProgressMonitor monitor)
```

The shorter overloads delegate to the 5-arg one, filling `null` for the missing script/controller and an
`EmptyProgressMonitor` for the missing monitor.

- **`make` is synchronous.** `BuildMakeService.doMake` builds a `MakeSequence`, then calls `CoreMakeTask.run(monitor)`
  *on the calling thread* and wraps the already-computed result in a `FutureValue`. The returned `Future` is always
  already complete; `future.get()` does not block and never throws `InterruptedException` in practice. Make runs on
  whatever thread you call it from.
- **Do not call it inside a read/write action.** Make manages its own model access internally (the `MakeSequence`
  clusterizer and each target take read/write locks as needed — see the `runReadAction` inside
  `MakeSequence.prepareClusters`). Wrap only the *resource collection* in a read action; call `make` outside any
  action.
- **`controller == null`** → `BuildMakeService` supplies `new IScriptController.Stub2(session)`, whose config monitor
  answers every facet query with its default option. This is the "just use sensible defaults" path.

## Scripts: prefer letting MPS build one (`script == null`)

`MakeSequence` (`core/make-runtime/.../make/dependencies/MakeSequence.java`) clusterizes the input resources by module
and, **for each cluster, builds a script only if the caller passed none**:

```
private void prepareSciptForCluster(Cluster cluster) {
  if (myDefaultScript != null) {
    cluster.setScript(myDefaultScript);
  } else {
    ScriptBuilder builder = cluster.createScriptBuilder(project.getComponent(FacetRegistry.class));
    cluster.setScript(myMakeSession.toScript(builder));
  }
}
```

So passing `script = null` (what `BaseGeneratorWorker` does) makes MPS compute the correct facet set per cluster
automatically. That is the robust default and avoids replicating facet resolution.

`execute-generators` instead builds one explicit script via `ScriptBuilder`: it walks all used languages (and the used
languages of their generators, transitively), pulls facets from each language's `MakeAspectDescriptor` and from the
`FacetRegistry`, and calls `.withFinalTarget(ITarget.Name("jetbrains.mps.make.facets.Make.make"))`. Its own source
carries a `// todo: not sure if we really need the final target to be Make.make all the time` note. Reach for an
explicit script only when you must constrain the target set (e.g. stop before compilation) and the controller cannot
express it; otherwise pass `null`.

**`ScriptBuilder.toScript()` fails soft:** with no requested facets it returns an `InvalidScript` (not an exception),
which then makes make fail. If you build scripts manually and get an unexplained failure, check that facets were
actually collected.

## `ModelsToResources` — model filtering happens here

Source: `core/resources/source_gen/.../ModelsToResources.java`.

- `ModelsToResources(models).resources()` keeps only models where `GenerationFacade.canGenerate(model)` is true —
  i.e. `model instanceof GeneratableSModel && model.isGeneratable()`. Non-generatable models (read-only, stubs,
  already-transient, etc.) are **silently dropped**.
- Surviving models are grouped by their owning `SModule` into one `MResource` per module.
- Consequence: an empty result means "nothing here is generatable", which is distinct from "generation failed". The
  reference tool maps this to a dedicated exit code (254, *nothing to generate*). Check for an empty resource list
  before calling `make`.
- `resources()` reads the model graph; call it inside a read action (the reference code does — the MPS source even
  leaves an `// XXX resources() needs model access, isn't it?` note confirming it).
- The constructor takes a `cleanMake` flag (default `false`) that is stamped onto each `MResource`; keep it consistent
  with the `MakeSession`'s clean-make flag.

## `MakeSession`

Source: `core/make-runtime/.../make/MakeSession.java`. Constructor:

```
MakeSession(@NotNull Project mpsProject, @NotNull IMessageHandler messageHandler, boolean cleanMake)
```

- `messageHandler` is `@NotNull` — a `null` handler is no longer supported. Use `IMessageHandler.NULL_HANDLER` for
  `/dev/null`, or a real handler to capture messages.
- `cleanMake = true` forces a full rebuild of the given resources; `false` makes only dirty models. `execute-generators`
  and `BaseGeneratorWorker` both pass `true`.

## Collecting errors: `IMessageHandler` + the reliability caveat

Make reports end-user-facing messages through the `IMessageHandler` you gave the `MakeSession`. Each `IMessage`
(`core/mps-messaging/source/.../IMessage.java`) carries:

- `getKind()` → `MessageKind` — one of `INFORMATION`, `WARNING`, `ERROR` (only these three; this is not a logging
  level enum).
- `getText()`, `getException()` (may be null), `getSender()`, `getHintObject()` (often the offending node/model,
  useful for locating the error), `getHelpUrl()`.

`IMessageHandler.handle(@NotNull IMessage)` is the single method. Handy defaults on the interface:
`NULL_HANDLER`, `compose(other)`, and `restrict(MessageKind.ERROR)` (drop anything below the given severity).

**`IResult` is not trustworthy on its own.** `IResult.isSucessful()` (note the misspelling — that is the real method
name; there is no `isSuccessful`) can return `true` even when errors were reported. The reference implementation keeps
its own `errorOccurred` flag that flips on any `MessageKind.ERROR` and treats the run as failed if either the result is
a failure *or* an error message was seen:

```
result.isSucessful && !errorOccurred  ->  success
result.isSucessful &&  errorOccurred  ->  "successful, but errors were reported"  -> failure
!result.isSucessful                   ->  failure
```

A make command should do the same: capture errors in the handler and combine with `isSucessful`. `future.get()` may
also return `null` — guard for it (`BaseGeneratorWorker` does).

### Every phase reports through the one handler (verified)

The `MakeSession`'s single `IMessageHandler` is the sink for **all** make phases — generation, textgen, Java/Kotlin
compilation, class reload — so one handler captures generator *and* compiler errors:

- **Generator / textgen errors** are reported by the generator through the handler `Generate_Facet`/`TextGen_Facet`
  pass it (`GenerationFacade(...).messages(mh)`). Text describes the problem; `getHintObject()` is the offending
  `SNode`/`SNodeReference`. Example (a broken reference in an expression):
  `textgen error: 'possible broken reference' in [expression] PlusExpression ... in <model>`.
- **Java compiler errors** flow `JavaCompile_Facet` → `ModuleMaker(mh)` → `JavaCompilerImpl.createErrorRecord`, which
  turns each `javax.tools.Diagnostic` of `Kind.ERROR` into `sender.error("<message> (<File>.java:<line>)", FileWithPosition(...))`.
  Two caveats confirmed in `JavaCompilerImpl`: compiler **warnings are dropped** to `trace`/log (they do *not* reach the
  handler — only compiler *errors* become messages), and errors are **capped at `MAX_ERRORS` = 20 per module**.
  `getHintObject()` is a `FileWithPosition` (the **generated** `.java` file + line, not the source node — mapping back
  to the node needs generation trace info).

`MessageSender.error(msg, hint)` builds the `IMessage` via `Message.createMessage(MessageKind.ERROR, sender, msg, hint)`,
so kind and hint are always set. Observed end-to-end by deliberately breaking a baseLanguage model: the make surfaced a
textgen `possible broken reference` error *and* four `illegal start of expression (Calculator.java:8)` / `: expected`
compiler errors, plus `Error executing target jetbrains.mps.make.facets.JavaCompile.compile`, all through the handler.
mops captures each message's kind and text in `MakeResponse.messages`; the structured `hintObject` location is currently
not surfaced.

## Dependencies are NOT expanded automatically — the caller owns the closure

This is the point most easily gotten wrong. **`make` builds exactly the modules whose resources you pass it. It never
pulls in a dependency module that you did not include.** It only *orders* the modules you gave it so that dependencies
among them build first.

The evidence is in `ModulesClusterizer` / `ModulesCluster`
(`core/make-runtime/.../make/dependencies/`), which `MakeSequence` runs over the input resources:

- `ModulesCluster.collectRequired` (its own comment): *"keep graph with dependencies, with vertexes for modules being
  clusterized only (i.e. no vertex for a module that is among dependencies but not being built)."*
- `ModulesCluster.fillEdges` computes each module's transitive compile dependencies via
  `new GlobalModuleDependenciesManager(modExt).getModules(Deptype.COMPILE)`, but then records graph edges **only to
  modules already in the input set** (`// record edges only to existing vertexes`, filtered with
  `NotNullWhereFilter`). Dependencies outside the input are used to compute build *order* and dropped, never added to
  the build.

So the clusterizer guarantees ordering (a used language module in the input is made before the module that uses it,
generators before their languages' clients, cycles compacted together) but not completeness. Neither MPS's headless
`BaseGeneratorWorker` nor the IDE "Make Module(s)" action expands the set either — both make just the modules handed to
them and rely on everything else being already built/loaded.

**Consequence for a `make` command that must make un-made dependencies (including indirect): compute the dependency
closure yourself and add it to the input.**

### Computing the closure: `GlobalModuleDependenciesManager`

`jetbrains.mps.project.dependency.GlobalModuleDependenciesManager` (`core/project/source/.../`, ships in
`mps-core.jar`) is the same primitive the clusterizer uses:

```
val closure: Collection<SModule> =
    GlobalModuleDependenciesManager(requestedModules)   // Collection<? extends SModule> or a single SModule
        .getModules(GlobalModuleDependenciesManager.Deptype.COMPILE)
```

- The result **includes the initial modules** plus their transitive dependencies of the requested `Deptype`.
- `Deptype` (all include used-language runtimes and stub paths, and the initial set):
  - `VISIBLE` — visible modules only, respecting reexport. No language runtimes.
  - `COMPILE` — visible + used-language runtimes, respecting reexport. This is what "must exist to compile these
    modules" means, and what the clusterizer uses for build ordering. **Use this** to decide what must be made before
    the targets.
  - `EXECUTE` — full transitive closure ignoring reexport, plus runtimes. Broader than needed for making.
- Runs in O(modules + deps), independent of the start-set size. Reads the module graph → call it inside a read action.
- Takes an optional `ErrorHandler` (default posts warnings) for dependencies that cannot be resolved.

Feed the closure through the normal path — `module.models` → `ModelsToResources(...).resources()`. `ModelsToResources`
drops every non-generatable module (deployed library languages, stubs, compiled solutions) via
`GenerationFacade.canGenerate`, so the make set naturally narrows to the **source** modules in the closure; already-built
library dependencies fall away on their own. The clusterizer then orders the survivors.

**`COMPILE` does not include a used language's source module (verified).** A plain solution that *uses* a language `L`
(its models import `L`) but has no module-level `<dependency>` on `L`'s module gets a `COMPILE` closure of **just
itself** — `GlobalModuleDependenciesManager` follows module dependencies and used-language *runtimes*, and MPS treats
the language itself as an already-built, deployed artifact, so `L`'s source module (`.mpl`, the one that regenerates
into classes) is never added. Confirmed empirically: `getModules(COMPILE)` over a solution using an unbuilt
same-project language returned only the solution, and the ensuing make failed with `No facets requested for the
script` — the lone cluster's one used language was unregistered (never built), so it contributed no make facet.

This matches MPS's own design: the make framework and the IDE "Make Module" action assume the languages a module uses
are already built and deployed. To honor "make the un-made language a module depends on", the closure must be augmented
by hand: walk the set and, for each used `SLanguage` that resolves to a **project** `Language` module
(`MetaAdapterFactory.getLanguage(module.moduleReference)` gives a project module's `SLanguage`, `SModule.getUsedLanguages()`
gives a module's used languages), add that language module and its generators, to a fixpoint. Then the language lands in
an earlier cluster; MPS builds and *registers* it, and — because each cluster's script is built lazily right before that
cluster runs (`MakeSequence.prepareSciptForCluster`) — the dependent solution's cluster resolves the now-registered
language's facets and generates cleanly. A whole-project make sidesteps all this only because it already includes every
language module. mops implements this augmentation in `ProjectMake.collectMakeSet`.

### "un-made" vs "make everything in the closure"

The make framework has no notion of "make only the un-made ones" that adds modules; the choice of *what to include* is
yours, and `cleanMake` only chooses full vs incremental for what you did include:

- **`cleanMake = false`** — incremental. For each input model the generator reuses per-root caches from
  `GenerationDependencies`, so up-to-date models cost little but are still visited. It does **not** skip a whole model
  for you.
- **Whole-model skip is a caller-side pre-filter.** `jetbrains.mps.generator.ModelGenerationStatusManager`
  (`mps-generator.jar`) answers `generationRequired(model)` by comparing the model's current content hash against the
  last-generated hash (and returns true for changed/uncomputable models). `getModifiedModels(models)` filters a
  collection down to those needing generation. `BaseGeneratorWorker` uses exactly this for its "skip unmodified models"
  mode.

Two workable strategies for "make the requested modules and any un-made dependency":

1. **Whole closure, incremental** — include the entire `COMPILE` closure (filtered to generatable modules) and make
   with `cleanMake = false`. Simplest and always correct; up-to-date dependencies are cheap incremental no-ops. Cost:
   every model in the closure is still visited each run.
2. **Closure filtered by generation status** — from the closure, keep the requested modules' models plus only those
   dependency models where `generationRequired` is true, then make. Faster on large closures. Relies on the same
   staleness signal the IDE uses; note it tracks *generation* staleness (model hash), not whether compiled `.class`
   output still exists on disk, so a dependency whose output was deleted but whose model is unchanged would be
   considered up-to-date. For a first cut, strategy 1 is the safe default.

Either way, because a dependency that is already made keeps its previously compiled output on its module's classpath,
the requested modules still compile against it even when it is skipped from this run.

### Making languages in the same run as their clients

If the closure contains a language (or generator) defined in the project *and* a module that uses it, the clusterizer
puts the language in an earlier cluster so it is generated and compiled first. Whether the freshly-made language is then
reloaded and used for the client's generation within the same make invocation is a known MPS subtlety (the reason the
IDE sometimes asks you to rebuild languages first); it has not been verified here. If cross-language-version issues
appear, making languages in a separate, prior make invocation from their clients is the conservative fallback.

## Optional: generation settings and the controller

- **Parallel generation.** `project.getComponent(GenerationSettingsProvider::class.java).generationSettings` exposes
  `isParallelGenerator`, `numberOfParallelThreads`, and `isStrictMode`. Parallel generation requires strict mode.
  `execute-generators` toggles these from CLI flags before calling make.
- **Skipping / configuring compilation.** To control the Java/Kotlin compile step, pass an `IScriptController.Stub2`
  built with a `JavaCompileFacetInitializer`:
  `new JavaCompileFacetInitializer().skipCompilation(true).setJavaCompileOptions(opts)`, then
  `new IScriptController.Stub2(session, initializer)`. `BaseGeneratorWorker` uses exactly this to offer a
  "generate but don't compile" mode. With a non-null controller, `BuildMakeService` does *not* substitute its default
  `Stub2` — the caller owns facet configuration.

## Threading and events summary

1. Collect models — inside `project.modelAccess.runReadAction { ... }`.
2. Build resources with `ModelsToResources(...).resources()` — also under read access.
3. (`BaseGeneratorWorker` calls `environment.flushAllEvents()` here, before and after make, to drain pending events.)
4. Call `BuildMakeService().make(...)` — **outside** any read/write action, on a normal thread. It runs synchronously.
5. `future.get()` (already complete) → combine `isSucessful` with your handler's error flag.
