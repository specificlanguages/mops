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

`ReloadManager.getInstance().flush()` in `mps-platform.jar` synchronously applies collected model/module reload
requests. The implementation is `ReloadManagerComponent` under
`workbench/mps-platform/jetbrains.mps.ide.platform/source_gen/jetbrains/mps/ide/platform/watching`.

MPS groups this work in a `ReloadSession`: a batch of reactions to detected file changes that have not yet been applied
to the in-memory repository. For example, refreshing an externally changed model file can collect a request to reload
that model. MPS normally processes the batch through a delayed queue; `flush()` applies it synchronously before
subsequent repository reads. Applying these requests does not by itself establish that compiled runtime classes have
been replaced; that requires the separate runtime invalidation
described below.

When there is no pending session, `flush()` returns without saving. When a session exists, it saves open projects before
applying the reload, as the normal queued reload path also does. Callers must account for native MPS save and reload
semantics when external file changes coexist with unsaved model changes.

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

Reload propagation includes dependent modules, not just the explicitly supplied modules. In
`core/kernel/source/jetbrains/mps/classloading/ModuleUpdater.java`, changed modules trigger
`visitIncomingDeep` on the classloading dependency graph to collect modules for unloading and loading. Thus a changed
solution can cause its transitive dependents to reload even when their own compiled files are unchanged. Verified with
an unchanged compiled consumer calling a method in an externally replaced dependency solution: the method returned
the new value in the same daemon, with the replacement class file's size and timestamp preserved. Propagation depends
on the relationships represented in MPS's classloading dependency graph.

Calling this method for modules whose compiled output changed, on the event-dispatch thread inside a write action
after file/model refresh, was verified to make the replacement runtime's concept name available and remove the
superseded name. File refresh alone and a matching generation hash are not proof of classloader replacement.

`SAbstractConcept.getConceptAlias()` (`mps-openapi.jar`,
`core/openapi/source/org/jetbrains/mps/openapi/language/SAbstractConcept.java`) exposes a compiled runtime value.
Its implementation in `SAbstractConceptAdapter` (`mps-core.jar`,
`core/kernel/source/jetbrains/mps/smodel/adapter/structure/concept/SAbstractConceptAdapter.java`) reads the registered
`ConceptDescriptor`, returning an empty string when no descriptor is available. This distinguishes a changed runtime
alias from merely reading the alias property of a reloaded structure source node.

## Java library changes and reusable MPS mechanisms

`GlobalModuleDependenciesManager.getModules(Deptype.EXECUTE)` in `mps-core.jar`
(`core/project/source/jetbrains/mps/project/dependency/GlobalModuleDependenciesManager.java`) collects the initial
modules, their transitive dependencies regardless of reexport flags, and runtime modules of used languages.
`JavaModuleFacet.getClassPath()` includes a module's compiled output and Java libraries, including directories and
archives. This is the same classpath used by `ModuleClassLoaderSupport.calcClassPath()` in
`core/kernel/source/jetbrains/mps/classloading/ModuleClassLoaderSupport.java`. These mechanisms can be reused to
discover runtime inputs, with `ClassLoaderManager.reloadModules()` handling reload propagation after a change is found.

`ModulesWatcher` maintains the classloading dependency graph from module events; it does not scan classpath file
contents. `ClassLoaderManager` processes repository module events and explicit reload requests. These classes are under
`core/kernel/source/jetbrains/mps/classloading`. No general content-change detector was found in these mechanisms.
IDEA file refresh alone did not replace a loaded Java library in the verified case: after replacement of a library
directory outside the project, an unchanged consumer still returned the old value. Detecting the changed contents and
explicitly reloading the owning module made the consumer return the new value.

External library directories, including a directory reached through a symbolic link, and JAR files were exercised with
their replacement class/JAR sizes and timestamps preserved. Content comparison detected the replacements; the
consumer's compiled output was unchanged and the daemon process remained
the same. Timestamp-only checks would not detect these replacements. Content comparison reads the watched classpath
files, so its cost grows with the total classpath size; shared entries can be fingerprinted once per scan.

This applies to `JavaModuleFacet.LoadClasses.ManagedByMPS`. Modules whose classes are managed by an IDEA/plugin
contributor delegate to that contributor's classloader; replacing it is not provided by this module-reload mechanism.
