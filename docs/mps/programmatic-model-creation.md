# Programmatic model creation and persistence format

**Question this answers:** how does an MPS client create and register a model with a chosen name, and how can it select
file-per-root persistence instead of the default single XML file?

Verified against MPS **2025.1.2** (`com.jetbrains:mps:2025.1.2`). Signatures were checked in the distribution jars;
behavior was read in the MPS 2025.1 source checkout. Source paths below are relative to the
[JetBrains/MPS](https://github.com/JetBrains/MPS) repository root.

## Public model-root API

`org.jetbrains.mps.openapi.persistence.ModelRoot` (`mps-openapi.jar`; source
`core/openapi/source/org/jetbrains/mps/openapi/persistence/ModelRoot.java`) exposes:

```java
@Nullable SModel createModel(@NotNull SModelName modelName)
```

The older `createModel(String)` overload is deprecated. Callers can use `canCreateModels()` and
`canCreateModel(SModelName)` to reject unsuitable roots before creation. Creation is a repository mutation and MPS's
workbench implementation invokes it inside a model command/write action; headless clients likewise need to use their
repository's command/write mechanism.

This interface does not provide a persistence-format parameter. The implementation decides the format.

## `DefaultModelRoot`: selecting the persistence format

The standard editable root is `jetbrains.mps.persistence.DefaultModelRoot` (`mps-core.jar`; source
`core/kernel/source/jetbrains/mps/persistence/DefaultModelRoot.java`). Its format-selecting overload is:

```java
@NotNull
SModel createModel(
    @NotNull SModelName modelName,
    @Nullable SourceRoot sourceRoot,
    @Nullable DataSourceType dataSourceType,
    @Nullable ModelFactoryType modelFactoryType
) throws ModelCannotBeCreatedException
```

For a normal single-file `.mps` model:

```java
SModel model = root.createModel(
    new SModelName("com.example.myModel"),
    null,
    PreinstalledDataSourceTypes.MPS,
    PreinstalledModelFactoryTypes.PLAIN_XML
);
```

For file-per-root persistence:

```java
SModel model = root.createModel(
    new SModelName("com.example.myModel"),
    null,
    PreinstalledDataSourceTypes.MODEL,
    PreinstalledModelFactoryTypes.PER_ROOT_XML
);
```

There is also a convenience method with the same pairing:

```java
@Nullable SModel createPerRootModel(
    @NotNull String modelName,
    @Nullable SourceRoot sourceRoot
) throws ModelCannotBeCreatedException
```

`PreinstalledDataSourceTypes` is in `jetbrains.mps.extapi.persistence.datasource` and
`PreinstalledModelFactoryTypes` is in `jetbrains.mps.persistence`, both in `mps-core.jar`.

Passing `null` for `sourceRoot` selects the first source root of kind `SourceRootKinds.SOURCES`; creation fails with
`NoSourceRootsInModelRootException` (a `ModelCannotBeCreatedException`) if none exists. Passing `null` for both format
parameters does **not** preserve an ambient/project preference: `DefaultModelRoot` explicitly defaults to
`PreinstalledDataSourceTypes.MPS` plus `PreinstalledModelFactoryTypes.PLAIN_XML`.

## What creation does

`DefaultModelRoot.createModel(...)`:

1. resolves the source root, data-source factory, and model factory;
2. derives the data-source location from the `SModelName` and source root;
3. checks `ModelFactory.canCreate(...)` and throws if files already exist or the combination is unsupported;
4. creates a model with a newly generated `SModelId` and the requested `SModelName`;
5. marks an editable model changed, associates it with the model root, and registers it with the owning module.

Registration is supported only when the owning module is an MPS `SModuleBase`; otherwise creation throws
`ModelCannotBeCreatedException`.

Creation does not itself persist the new model. Call `EditableSModel.save()` while still in the appropriate platform
write action when the model must exist on disk. For file-per-root, the resulting data source is multi-stream: a
`.model` header plus `.mpsr` root streams. See `core/persistence/source/jetbrains/mps/persistence/FilePerRootModelFactory.java`.

## Practical choice for a client

Find the intended writable `ModelRoot` on the target module, check `canCreateModel(name)`, and call the public
`createModel(SModelName)` when its configured/default format is acceptable. When explicit format selection is part of
the operation, require a `DefaultModelRoot` and use the four-argument overload (or `createPerRootModel`). Do not
instantiate `FilePerRootModelFactory` directly: the root coordinates data-source naming, collision checks, root
association, and module registration.

## Planning without creating

`DefaultModelRoot.canCreateModel(SModelName)` validates only its default single-file persistence. Its implementation
constructs the default `MPS` data source and asks the default model factory whether it can create the model; it is not
a format-aware preflight for file-per-root creation.

A client that needs a format-aware dry run can mirror the preparation performed by `DefaultModelRoot`:

1. obtain `DataSourceFactoryRuleService` and `ModelFactoryRegistry` from the MPS project components;
2. use `DataSourceFactoryBridge` to create the prospective regular-file or per-root data source;
3. obtain the `PLAIN_XML` or `PER_ROOT_XML` model factory;
4. call `ModelFactory.canCreate(...)` with the bridge result's converted loading options.

Creating the prospective data source calculates and wraps its location but does not write it. `DataSource.getLocation()`
returns that planned location. These bridge and registry classes are MPS internal APIs rather than OpenAPI and should be
reverified when changing MPS versions. See
`core/kernel/source/jetbrains/mps/persistence/DataSourceFactoryBridge.java` and
`core/openapi/source/org/jetbrains/mps/openapi/persistence/ModelFactory.java`.

## Recovering from a failed save

An MPS command is a write action with undo support, not a transaction. An exception does not automatically undo model
registration or filesystem writes, and saving a data source is not transactional.

For a newly created model whose first save fails, `SModuleBase.unregisterModel(SModelBase)` removes the model from its
module and detaches its in-memory data without deleting data-source files. `ModelDeleteHelper.detachFromModule()` wraps
the same operation. By contrast, `ModelDeleteHelper.delete()` also deletes the data source and generated artifacts; do
not use it blindly for failure recovery because a partial save or race may have left files that cannot safely be assumed
to belong exclusively to the failed operation.

Static source inspection cannot guarantee that `EditableSModel.save()` leaves either all or none of its files when it
fails. A client should therefore unregister the in-memory model and either retain possible filesystem residue with a
diagnostic, or delete only paths whose prior absence and ownership it established independently. See
`core/kernel/source/jetbrains/mps/extapi/module/SModuleBase.java` and
`core/model/source/jetbrains/mps/model/ModelDeleteHelper.java`.

## Finding and choosing a model root

`org.jetbrains.mps.openapi.module.SModule` (`mps-openapi.jar`; source
`core/openapi/source/org/jetbrains/mps/openapi/module/SModule.java`) exposes:

```java
Iterable<ModelRoot> getModelRoots()
```

There is no API for “the default model root.” A module owns an ordered iterable of roots, and choosing the first root
unconditionally is unsafe: modules may contain read-only roots, Java stub roots, or several writable roots pointing at
different physical locations.

For automatic creation, MPS code generally scans in module order and uses the first eligible root:

```java
for (ModelRoot root : module.getModelRoots()) {
  if (root.canCreateModels() && root.canCreateModel(modelName)) {
    return root.createModel(modelName);
  }
}
throw new IllegalStateException("No model root can create " + modelName);
```

This pattern appears in
`workbench/mps-platform/jetbrains.mps.ide.platform/source_gen/jetbrains/mps/project/modules/LanguageAndSolutionsProducer.java`.
Other MPS callers sometimes check only `canCreateModel`, which is sufficient for `DefaultModelRoot` because its
implementation first checks `canCreateModels`; checking both expresses the intended generic `ModelRoot` contract.

Treat “first eligible root” as a deterministic non-interactive policy, not as an MPS-designated default. If more than
one eligible root exists, a client that needs to preserve the user's intended physical layout should expose the choice
or require a root identifier. `ModelRoot` has no stable ID in the OpenAPI; useful display/disambiguation values are
`getPresentation()` and `getType()`, with iterable position only as a last-resort selector.

### What the MPS New Model dialog does

The standard dialog explicitly offers a **Model root** combo box. In
`workbench/mps-workbench/jetbrains.mps.ide/source_gen/jetbrains/mps/ide/dialogs/project/creation/NewModelDialogDefaultSettings.java`
it walks `module.getModelRoots()` in order and includes:

- every root for which `canCreateModels()` is true; and
- for a language module, a `FileBasedModelRoot` even when it currently reports false, because the creation helper can
  add/fix the special accessory-model source root.

Swing selects the first combo-box entry initially, so accepting the dialog unchanged chooses the first eligible root;
the user can choose another. The dialog validates the selected root and name through `ModelNameValidator`, which calls
`canCreateModel`.

The dialog also offers a **Storage format** combo when the selected root is a `DefaultModelRoot`. It passes the chosen
`ModelFactoryType` to the four-argument creation overload. It does not offer a separate source-root selector:
`DefaultModelRoot` normally chooses its first `SourceRootKinds.SOURCES` source root. Thus a model root with multiple
source roots has another implicit “first” choice below the UI-visible model-root choice.

### Recommended non-interactive policy

For a command that has no explicit model-root option:

1. collect roots satisfying both `canCreateModels()` and `canCreateModel(name)`;
2. fail if there are none;
3. use the sole candidate when there is exactly one;
4. if there are several, either fail with their presentations and ask for an explicit selection, or deliberately use
   the first candidate and document that this mirrors the New Model dialog's initial selection.

Silent first-candidate selection is reasonable for compatibility with MPS UI defaults, but it can put a model in the
wrong directory in a multi-root module. Explicit ambiguity handling is the safer general-purpose API design.

## Re-verification triggers

- Recheck `DefaultModelRoot.Defaults` when changing MPS versions; this is where the implicit single-file default lives.
- Recheck command/write requirements with a runtime probe in the actual host environment. Source usage confirms the
  workbench executes creation and save in a command/write action, but static inspection alone cannot prove every
  headless repository implementation's runtime assertions.
