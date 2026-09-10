# Reloading externally edited project settings

Verified against `com.jetbrains:mps:2025.1.2`, the JetBrains/MPS 2025.1 source, and the bundled IntelliJ platform.
Runtime probes used an open directory-based project with application saving enabled.

## Project membership uses the IntelliJ component store

`jetbrains.mps.project.StandaloneMPSProject` implements `PersistentStateComponent<Element>` and declares
`@State(name = "MPSProject", storages = @Storage("modules.xml"))`. It ships in `mps-workbench.jar`.
`loadState(Element)` reads the project descriptor and calls `update()`, which reconciles the project module paths under
model write access. IntelliJ expands `$PROJECT_DIR$` before handing the state to MPS.

Changes to `.mps/modules.xml` therefore enter through IntelliJ's settings storage machinery, separately from the MPS
model/module reload sessions described in [external-file-runtime-refresh.md](external-file-runtime-refresh.md).
`ReloadManager.flush()` only drains those MPS sessions. It does not drain IntelliJ's component-store reload queue.

`com.intellij.configurationStore.StoreReloadManagerImpl` collects changed storages from VFS events, disables saving
those storages while their reload is pending, and schedules processing through a coroutine flow with a 300 ms debounce.
Reload processing dispatches to the EDT. Its `reloadChangedStorageFiles()` suspend operation provides an explicit drain.
A synchronous host can call it through `runBlocking` on its request thread, outside MPS model access and off the EDT,
so the EDT remains available to apply the reloaded state.

## Synchronous refresh sequence

The following sequence was exercised in an open MPS 2025.1.2 IDEA environment:

1. On the EDT, synchronously refresh the project's `.mps` directory recursively with
   `IFile.refresh(new DefaultCachingContext(true, true))`.
2. Back on the request thread, call
   `runBlocking { StoreReloadManager.getInstance(ideaProject).reloadChangedStorageFiles() }`.
3. Refresh the current project module directories and drain `ReloadManager` before repository queries.

External module additions are visible to the next request without a sleep. External removals are applied before
creating another solution, and survive the subsequent project save.

Refreshing only existing module directories misses `.mps/modules.xml`. Explicitly refreshing the settings file without
draining the component-store reload is also insufficient: membership can still be stale when VFS refresh returns.

A save without an in-memory project change leaves an undetected external edit intact. Adding another solution in memory
and saving before refreshing the settings file can overwrite an external removal. Storage-level saving suppression only
helps once the external change has been detected; it does not protect edits that have not reached VFS/settings reload.
See [project-settings-save.md](project-settings-save.md) for dirty-state saving and concurrency limits.

These probes cover module membership in a directory-based project's `modules.xml`. They do not establish behavior for
file-based `.mpr` projects, every other project settings component, malformed XML, or settings requiring a full project
reopen.

## Sources

- JetBrains/MPS: `workbench/mps-workbench/source/jetbrains/mps/project/StandaloneMPSProject.java`
- JetBrains/MPS: `workbench/mps-platform/jetbrains.mps.ide.platform/source_gen/jetbrains/mps/ide/platform/watching/ReloadManagerComponent.java`
- JetBrains/intellij-community: `platform/configuration-store-impl/src/StorageVirtualFileTracker.kt`
- JetBrains/intellij-community: `platform/configuration-store-impl/src/StoreReloadManagerImpl.kt`
- JetBrains/intellij-community: `platform/platform-impl/src/com/intellij/configurationStore/StoreReloadManager.kt`
