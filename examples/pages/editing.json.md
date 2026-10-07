# JSON edits

Use batches when you want explicit edit operations and constraint diagnostics. This one-liner renames a node:

```sh
{{rename-node}}
```

Copy an existing test/root into another model and rename the copy in the same batch (`MODEL_REF` is the destination
model's serialized reference). Single shell quotes preserve the batch-local `$copy` alias:

```sh
{{copy-root}}
```

Copy a member subtree into a class, preserving its references and assigning fresh node IDs:

```sh
{{copy-member}}
```

{{table:commands|Command}}

The default `best-effort` constraint mode blocks violations but warns and skips checking concepts whose languages are
unloaded. Constraint checks are distinct from a full model check.
