# Retarget references and update model imports

To replace references to one node across editable project sources, collect usages before changing them:

```groovy
{{retarget-usages}}
```

When the replacement belongs to a different model, update the importing model explicitly. `ModelImports` is a native MPS
API; call `model.save()` inside the command to persist these import changes:

```groovy
{{replace-import}}
```

Verify the replacement nodes and affected imports before applying these operations.
