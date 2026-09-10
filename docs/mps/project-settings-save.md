# Saving project settings in a headless MPS environment

Verified with `com.jetbrains:mps:2025.1.2` and its bundled IntelliJ platform.

`jetbrains.mps.project.MPSProject.save()` (`mps-platform.jar`) delegates to
`com.intellij.openapi.project.Project.save()`. It saves project settings, including the module paths in
`.mps/modules.xml`; `SRepository.saveAll()` saves module descriptors and models separately.

The IntelliJ implementation, `com.intellij.openapi.project.impl.ProjectImpl.save()` (`app.jar`), returns immediately
when `ApplicationManagerEx.getApplicationEx().isSaveAllowed()` is false. `ApplicationImpl` defaults this flag to false
in headless mode unless `allow.save.application.headless` is set. A successful return from `MPSProject.save()` therefore
does not establish that project settings were written.

An application that supports saving throughout its lifetime can start with
`-Dallow.save.application.headless=true`. This enables the application's save-allowed flag during initialization.
For hosts that keep saving disabled, an explicit save can temporarily enable the flag and restore it in `finally`.
Both configurations were exercised with MPS 2025.1.2 and persisted newly added solution module paths.

Call `MPSProject.save()` on the EDT outside MPS model access. Save repository contents under model write access before
saving project settings; calling project save inside an MPS write command hung in a runtime probe.

Enabling application saving does not change `Application.isHeadlessEnvironment()` or activate listeners registered with
`activeInHeadlessMode="false"`, including `IdeMPSFileSaver`. Project-settings saving and repository saving remain distinct
operations. See [model-save-lifecycle.md](model-save-lifecycle.md) and
[external-file-runtime-refresh.md](external-file-runtime-refresh.md).

`ProjectBase.addModule(SModule)` records the descriptor path with the project's module loader and associates the module
with the project repository. A module can therefore be visible in the current project session while absent from the
saved project module list.

External edits to this list use IntelliJ's component-store reload mechanism; see
[project-settings-reload.md](project-settings-reload.md).

## Dirty-state saving

IntelliJ's `XmlElementStorage` serializes component state and compares it with cached state through
`StateMap.setStateAndCloneIfNeeded`. Equal XML elements (or equal serialized state bytes) produce no changed-state map.
`XmlElementStorage.SaveSessionProducer.createSaveSession()` returns null when that map is absent or saving is disabled
for the storage. Thus a normal project save writes changed settings storage, not every project file. A changed component
can cause its containing XML file to be written; this is not a per-element merge with simultaneous filesystem edits.

Runtime tests of directory-based MPS projects verified that repeated read requests followed by project saving preserve
both the contents and modification time of clean `.mps/modules.xml`. An external edit made during a read-only request,
after refresh and before save, also survived the clean save.

Reloading external settings before a request incorporates already-existing changes into the cached state used for the
next save. If another process edits the same settings component while the request changes it too, dirty-state saving
does not provide conflict-free merging or cross-process locking. The normal IntelliJ storage behavior remains in force.

Source locations:

- JetBrains/MPS: `workbench/mps-platform/source/jetbrains/mps/project/MPSProject.java`
- JetBrains/MPS: `core/project/source/jetbrains/mps/project/ProjectBase.java`
- JetBrains/MPS: `workbench/mps-workbench/source/jetbrains/mps/workbench/dialogs/project/newproject/ProjectFactory.java`
  also demonstrates temporarily enabling saving and restoring the flag.
- JetBrains/intellij-community: `platform/platform-impl/src/com/intellij/openapi/project/impl/ProjectImpl.kt`
- JetBrains/intellij-community: `platform/platform-impl/src/com/intellij/openapi/application/impl/ApplicationImpl.java`
- JetBrains/intellij-community: `platform/configuration-store-impl/src/XmlElementStorage.kt`
- JetBrains/intellij-community: `platform/configuration-store-impl/src/StateMap.kt`
