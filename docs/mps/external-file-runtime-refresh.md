# Refreshing external files and compiled runtimes

Verified against `com.jetbrains:mps:2025.1.2` and the JetBrains/MPS 2025.1 source. Source paths are relative to that
checkout. A running IDEA environment was exercised with a language built in another process: after its model sources,
generation records, and compiled output were copied over the open project's language, the renamed concept resolved and
the previous concept name stopped resolving without restarting the environment.

## File refresh and model reload

`IFile.refresh(CachingContext)` and `DefaultCachingContext(boolean synchronous, boolean recursive)` are in
`mps-core.jar`, under `core/vfs/source/jetbrains/mps/vfs`. Passing `true, true` requests synchronous recursive refresh.
For IDEA-backed files, `IdeaFileSystem.refresh(CachingContext, Collection<CachingFile>)` in `mps-platform.jar`
(`workbench/mps-platform/source/jetbrains/mps/ide/vfs/IdeaFileSystem.java`) delegates to
`VfsUtil.markDirtyAndRefresh(!synchronous, recursive, true, files)`. This explicitly marks cached files dirty before
refreshing them, so it does not depend on an operating-system watcher having reported the changes.

`ReloadManager.getInstance().flush()` in `mps-platform.jar` synchronously commits the pending MPS reload session.
The implementation is `ReloadManagerComponent` under
`workbench/mps-platform/jetbrains.mps.ide.platform/source_gen/jetbrains/mps/ide/platform/watching`. File refresh can
queue MPS model/module reloads; flushing completes that work before subsequent repository reads. When there is no
pending reload session, `flush()` returns without saving. For a pending session, it saves open projects before applying
the reload, as the normal queued reload path also does. Callers must account for native MPS save and reload semantics
when external file changes coexist with unsaved model changes.

The synchronous refresh and flush sequence was executed on the IDEA event-dispatch thread without an enclosing MPS
read or write action. Refresh can change the repository and invalidate retained model/node objects.

## Compiled classes need runtime invalidation

Updating files does not itself establish that the registered language runtime uses the new classes. A runtime can
continue exposing its old concept name after an externally built replacement is present on disk.

`ClassLoaderManager.reloadModules(Iterable<? extends SModule>)` in `mps-core.jar`
(`core/kernel/source/jetbrains/mps/classloading/ClassLoaderManager.java`) recreates module classloaders by reporting
module-change events. It requires MPS write access. The overload with a `ProgressMonitor` performs the same operation.
Repository modules must still be registered when passed to it. Changes propagate through the classloading layer and
language registry; see [runtime unloading](module-runtime-unloading.md).

Calling this method for modules whose compiled output changed, on the event-dispatch thread inside a write action
after file/model refresh, was verified to make the replacement runtime's concept name available and remove the
superseded name. File refresh alone and a matching generation hash are not proof of classloader replacement.

`SAbstractConcept.getConceptAlias()` (`mps-openapi.jar`,
`core/openapi/source/org/jetbrains/mps/openapi/language/SAbstractConcept.java`) exposes a compiled runtime value.
Its implementation in `SAbstractConceptAdapter` (`mps-core.jar`,
`core/kernel/source/jetbrains/mps/smodel/adapter/structure/concept/SAbstractConceptAdapter.java`) reads the registered
`ConceptDescriptor`, returning an empty string when no descriptor is available. This distinguishes a changed runtime
alias from merely reading the alias property of a reloaded structure source node.
