# Refreshing a build project's module imports from disk

Verified signatures against `com.jetbrains:mps:2025.1.2` with `javap`; behavior read from JetBrains/MPS 2025.1
source at commit `f4d90532bcac5e0339b3161cec38abf49567cffb`. No runtime probe was run for this operation; headless
execution, threading, and lifecycle requirements remain unverified.

## Direct operation

The build-language intention `Reload Modules From Disk` delegates its entire execution to:

```java
ModuleLoader loader = new ModuleLoader(buildProject, null,
    new LogHandler(Logger.getLogger(ModuleLoader.class)));
loader.checkAllModules(ModuleChecker.CheckType.LOAD_IMPORTANT_PART);
```

It does not use the `EditorContext`. The direct operation is therefore a useful integration entry point without
intention discovery or editor selection. A two-argument constructor delegates to the same null-generation-context
path and permits collecting diagnostics:

```java
import jetbrains.mps.build.mps.util.ModuleChecker;
import jetbrains.mps.build.mps.util.ModuleLoader;
import jetbrains.mps.messages.IMessageHandler;

// buildProject is the attached jetbrains.mps.build.structure.BuildProject SNode.
// Invoke inside the host's model mutation command; collect diagnostics before deciding to save.
ModuleLoader loader = new ModuleLoader(buildProject, messageHandler);
loader.checkAllModules(ModuleChecker.CheckType.LOAD_IMPORTANT_PART);
```

`messageHandler` implements `IMessageHandler`. These are public methods on generated build-language utility classes,
not a version-independent platform API. The classes ship in
`plugins/mps-build/languages/build/jetbrains.mps.build.mps.jar` within the MPS distribution, rather than `lib/`.
Their build-language dependencies must be available through the host's module/classloading integration.

Exact relevant signatures:

```java
public ModuleLoader(@NotNull SNode buildProject, @NotNull IMessageHandler msgHandler);
public ModuleLoader(@NotNull SNode buildProject, @Nullable TemplateQueryContext genContext,
                    @NotNull IMessageHandler msgHandler);
public void checkAllModules(ModuleChecker.CheckType type);
public ModuleLoader useFileSystem(FileSystem fs);
```

The two-argument path is also used by MPS's `RefreshTestProject_Action` after creating/replacing a build project.
Construction with a null generation context uses `Context.defaultContext()` and initializes path conversion and
visible-module collection from the build project.

## Scope and effects

The argument is the **build project model node**, not the IDE project or the module containing the build model.
`checkAllModules` visits module entries in the project's `parts`, both direct entries and entries in
`BuildMps_Group.modules`, and skips entries without a `path`. Language generators are handled by `ModuleChecker`.
It reads the referenced descriptor files using `DescriptorIOFacade` and temporarily loads modules into a private
repository to inspect their properties.

This refreshes existing build module entries; it does not discover unlisted modules by scanning directories. The
build project must already contain the module imports and usable paths. Dependencies are resolved through
`VisibleModules`; missing visible dependencies can be reported as errors.

`LOAD_IMPORTANT_PART` selects partial import: `(doCheck=false, doPartialImport=true, doFullImport=false)`.
Among the effects are updating names/UUIDs, extracted dependencies, language runtime/accessory/extended-language
information, devkit exports, generator information, extracted model source roots, and Java compilation kind.
Obsolete extracted entries can be removed. It reads descriptors from disk, so unsaved edits to live module
descriptors are not its input.

`LOAD_ALL` is a different operation, with `(true, false, true)`: it adds full-import processing such as local
dependencies and dependency optimization, and clears the compact flag. Use `LOAD_IMPORTANT_PART` for the intention's
behavior.

The method mutates the build model in memory and returns `void`; it does not save the model, generate the build
script, or run the build. It reports errors through the message handler and can continue processing other modules.
Normal return therefore does not establish success or atomicity. Collect errors and apply the host's mutation and
save/failure policy, including the possibility of partial changes. It is not a refresh of the live project's module
repository or classloaders.

The private repository is cleaned up after processing. That cleanup is not enclosed in a `finally` block in this
version, so unexpected exceptions can bypass it. Runtime integration should exercise both success and failure
paths. Source inspection alone does not establish that execution is EDT-free or works in a particular headless host.

## Executing through intentions

The definition key is:

```text
jetbrains.mps.build.mps.intentions.ReloadModulesFromDisk_Intention
```

The factory is registered for `jetbrains.mps.build.structure.BuildProject`, is available in child nodes, and is not
parameterized. Applicability requires a descendant of concept `BuildMps_AbstractModule`. With a working editor
context, discover this key on the build project and execute the returned executable on that build project through
the editor command path described in [intentions.md](intentions.md).

The direct `ModuleLoader` call performs the same operation without the intention manager's editor/typechecking
dependencies. It does not repeat the intention's applicability check, so the caller must supply the proper build
project node.

## Sources

Paths relative to the JetBrains/MPS repository:

- `plugins/mps-build/languages/build.mps/source_gen/jetbrains/mps/build/mps/intentions/ReloadModulesFromDisk_Intention.java`
- `plugins/mps-build/languages/build.mps/source_gen/jetbrains/mps/build/mps/intentions/IntentionsDescriptor.java`
- `plugins/mps-build/languages/build.mps/source_gen/jetbrains/mps/build/mps/util/ModuleLoader.java`
- `plugins/mps-build/languages/build.mps/source_gen/jetbrains/mps/build/mps/util/ModuleChecker.java`
- `plugins/mps-build/languages/build/source_gen/jetbrains/mps/build/util/Context.java`
- `plugins/mps-build/pluginSolutions/build.mps.testManifest.pluginSolution/source_gen/jetbrains/mps/build/mps/testManifest/pluginSolution/plugin/RefreshTestProject_Action.java`

The [MPS documentation](https://www.jetbrains.com/help/mps/removing-bootstrapping-dependency-problems.html) also
describes running this intention after correcting module dependencies to update the build script's dependency
structure.
