<!-- Generated from examples/pages and examples/specs; run :cli:updateExamples. -->

# Retarget references and update model imports

To replace references to one node across editable project sources, collect usages before changing them:

<!-- markdownlint-disable MD013 -->

```groovy
project.command { def old = mops.lookup.requireNode('OLD_REF'); def replacement = mops.lookup.requireNode('NEW_REF'); def usages = []; mops.search.eachUsageOf(old, project.scope) { usages << it }; usages.each { it.sourceNode.references[it.link.name] = replacement }; usages.size() }
```

<!-- markdownlint-enable MD013 -->

When the replacement belongs to a different model, update the importing model explicitly. `ModelImports` is a native MPS
API; call `model.save()` inside the command to persist these import changes:

<!-- markdownlint-disable MD013 -->

```groovy
project.command { def model = mops.lookup.requireModel('sample.model'); def old = mops.lookup.requireModel('old.model'); def replacement = mops.lookup.requireModel('new.model'); def imports = new jetbrains.mps.smodel.ModelImports(model); imports.removeModelImport(old.reference); imports.addModelImport(replacement.reference); model.save(); model }
```

<!-- markdownlint-enable MD013 -->

Verify the replacement nodes and affected imports before applying these operations.
