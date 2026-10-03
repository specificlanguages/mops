# Retarget references and update model imports

To replace references to one node across editable project sources, collect usages before changing them:

<!-- markdownlint-disable MD013 -->

```groovy
{{retarget-usages}}
```

<!-- markdownlint-enable MD013 -->

When the replacement belongs to a different model, update the importing model explicitly. `ModelImports` is a native MPS
API; call `model.save()` inside the command to persist these import changes:

<!-- markdownlint-disable MD013 -->

```groovy
{{replace-import}}
```

<!-- markdownlint-enable MD013 -->

Verify the replacement nodes and affected imports before applying these operations.
