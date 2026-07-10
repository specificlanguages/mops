# Model save lifecycle (does MPS auto-save?)

**Question this answers:** can the running MPS platform persist an in-memory model change to disk without mops asking —
via idle auto-save, save-on-close, save-on-command, or a background flush? This matters for `model edit` atomicity:
mops reverts a failed batch with `EditableSModel.reloadFromSource()`, which only recovers the on-disk state, so it is
safe only if nothing was written behind mops's back.

**Answer:** in the headless daemon, no. Models are written to disk **only** when mops explicitly calls
`SModel.save(...)` (through `WriteTransaction.WriteScope.saveWithResolveInfo`). MPS's one automatic model-save bridge is
gated off in headless mode, and nothing else on the daemon's read/edit paths saves.

Verified against MPS **2025.1.2** (`com.jetbrains:mps:2025.1.2`). Source references below are to the
[JetBrains/MPS](https://github.com/JetBrains/MPS) repository, paths relative to its root.

## The only automatic save bridge: `IdeMPSFileSaver`

MPS does have an automatic save, but it is a workbench feature, not a kernel one.

- `jetbrains.mps.ide.save.IdeMPSFileSaver` (`mps-platform.jar`; source
  `workbench/mps-platform/source/jetbrains/mps/ide/save/IdeMPSFileSaver.java`) implements
  `com.intellij.openapi.fileEditor.FileDocumentManagerListener`. Its `beforeAllDocumentsSaving()` runs
  `SaveRepositoryCommand` for **every open project's whole repository** — every dirty model, not just the ones a caller
  touched. Its own class comment: *"it saves everything whenever the platform saves everything."*
- It fires off the IntelliJ platform's "save all documents" event (`FileDocumentManager` / `SaveAndSyncHandler` — frame
  deactivation, idle auto-save, project/app close). That is the mechanism that would otherwise flush edits behind an
  API caller's back.

### Why it does not fire in the daemon

It is registered with `activeInHeadlessMode="false"`:

```
# META-INF/MPSCore.xml (mps-workbench.jar)
<listener class="jetbrains.mps.ide.save.IdeMPSFileSaver"
          topic="com.intellij.openapi.fileEditor.FileDocumentManagerListener"
          activeInHeadlessMode="false"/>
```

The platform honors that flag when registering message-bus listeners:
`com.intellij.serviceContainer.ComponentManagerImpl` reads `Application.isHeadlessEnvironment()` and skips any
`ListenerDescriptor` whose `activeInHeadlessMode` is false (`app.jar`; `ListenerDescriptor` in `util-8.jar`). Bytecode
branch (`javap -c ComponentManagerImpl`): when the headless flag is set and `activeInHeadlessMode == false`, the
descriptor is dropped instead of added to the topic's listener list.

The daemon runs the platform **headless**: it boots through the itemis project loader with `EnvironmentKind.IDEA`
(`MopsDaemonCommand.runWithMpsAccess`), i.e. `jetbrains.mps.tool.environment.IdeaEnvironment`, whose `init()` starts the
app via `MPSHeadlessPlatformStarter` and sets `java.awt.headless=true`
(`workbench/mps-platform/.../IdeaEnvironment.java`). So `Application.isHeadlessEnvironment()` is true and
`IdeMPSFileSaver` is never registered — no idle/deactivation/close save reaches the repository.

## Nothing else on the daemon paths saves

- Every other caller of `SaveRepositoryCommand` / `EditableSModelBase.save` is an interactive workbench action —
  refactoring dialogs, module/model properties, `MakeActionImpl`, migration wizards, VCS conflict tracking. None sit on
  mops's read / `model edit` paths.
- The `autosave` string in `EditableSModelBase.resolveConflict0()` is a *warning message* shown on external-file
  conflict (it even notes MPS does "saveAll on each fs reload" — that is the `IdeMPSFileSaver` path above), not a
  self-scheduled save.
- Project dispose does not save: `ProjectBase.dispose()` has no `save()` call, and the itemis loader disposes the
  project at the end of `executeWithProject` without a save. Closing the daemon drops unsaved in-memory changes rather
  than flushing them.

## Consequence for `model edit` atomicity

Because no background save exists in the daemon, `reloadFromSource()` is a sound revert for a batch that failed **before**
mops called save: the on-disk copy is still the pre-batch state, and reloading discards the in-memory mutations. The
real limits on `model edit` atomicity are therefore the explicit-save ones, not a surprise auto-save:

- `saveWithResolveInfo` writes affected models one at a time and stops at the first failure, so a batch spanning several
  models can leave earlier models already persisted.
- `reloadFromSource()` reverts to on-disk state, so any in-memory changes a model already held at batch start are
  discarded along with the batch.

## Gotchas / re-verify triggers

- This guarantee is contingent on **headless**. If mops ever ran the platform non-headless, registered its own
  `FileDocumentManagerListener`, or called `FileDocumentManager.saveAllDocuments()`, `IdeMPSFileSaver` would wake up and
  save the **entire repository** — every dirty model across every open project, not just the edit batch's models.
- The `activeInHeadlessMode` gate is an IntelliJ platform contract, not an MPS one; re-verify against
  `ComponentManagerImpl` if the platform build under MPS changes materially.
