# Running the full Model Check headlessly

How to run MPS's own **Model Check** — every checker in the platform registry (typesystem plus checking/constraint
rules) — over one model from code, read-only, and collect the reported items.

Verified against the MPS 2025.1.2 distribution jars (`com.jetbrains:mps:2025.1.2`) and the
[JetBrains/MPS](https://github.com/JetBrains/MPS) source.

## Jars

`CheckerRegistry` lives in `lib/mps-project-check.jar`; everything else below is in `lib/mps-core.jar`. The daemon adds
`mps-project-check.jar` to its compile-only MPS classpath for this reason (see `daemon/build.gradle.kts`).

## API

```kotlin
// CheckerRegistry is a platform CoreComponent, reachable from the Project alone — no Environment needed.
val registry = project.getComponent(CheckerRegistry::class.java) ?: error("CheckerRegistry not available")

val extractor = object : ModelCheckerBuilder.ModelsExtractorImpl() {
    override fun includeModel(candidate: SModel): Boolean = candidate == model
}
extractor.includeStubs(false)

val checker = ModelCheckerBuilder(extractor).createChecker(registry.checkers)
val collector = CollectConsumer<IssueKindReportItem>()
checker.check(
    ModelCheckerBuilder.ItemsToCheck.forSingleModel(model),
    project.repository,
    collector,
    EmptyProgressMonitor(),
)
```

- `Project.getComponent(Class)` is nullable; it delegates to the platform `ComponentHost`, so it returns the same
  application-level `CheckerRegistry` MPS's own `ModelCheckerSettings` / `MPSValidationComponent` use.
- `CheckerRegistry.getCheckers()` returns `List<IChecker<?, ?>>`, accepted directly by
  `ModelCheckerBuilder.createChecker`.
- `ItemsToCheck.forSingleModel(SModel)` builds the single-model work item; `includeStubs(false)` mirrors MPS's headless
  checking.
- `CollectConsumer` and `EmptyProgressMonitor` implement the `org.jetbrains.mps.openapi.util` `Consumer` / `ProgressMonitor`
  the checker expects.

## Read action

`check(...)` runs its work synchronously on the calling thread, *outside* the short internal read action it takes only
to build the task. The traversal, reading `item.getMessage()`, and resolving each `PathObject` all touch model data, so
**the caller must hold a read action around the whole `check(...)` call and around the result mapping**. Running it
inside `project.modelAccess.computeReadAction { ... }` (the daemon's `MpsAccess.read`) satisfies this. No EDT is needed —
a plain read action is enough.

## Mapping a report item

`IssueKindReportItem` (its severity/message come from the `ReportItem` supertype):

- `getSeverity(): MessageStatus` — a `Comparable` enum ordered `OK < WARNING < ERROR`.
- `getMessage(): String`.
- `getIssueKind(): IssueKindReportItem.ItemKind?` — `.getChecker().getName()` is the checker category (e.g.
  `unresolved reference`, `typesystem`), `.getSpecialization()` a finer discriminator. There is no per-rule stable id
  exposed.
- `IssueKindReportItem.PATH_OBJECT.get(item)` returns a nullable `PathObject`; its subclasses `NodePathObject`,
  `ModelPathObject`, `ModulePathObject` each expose a covariant `resolve(SRepository)` returning `SNode` / `SModel` /
  `SModule` (nullable if the reference no longer resolves). A node-level finding resolves to the offending `SNode`; a
  model-level finding has no node.
