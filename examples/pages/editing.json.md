# JSON edits

Use batches when you want explicit edit operations and constraint diagnostics. `mops explain edit` describes the
operations and how a batch applies them. This one-liner renames a node:

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

`--constraints` decides whether a constraint violation blocks the batch; `mops edit model --help` lists the modes.
Constraint checks are distinct from a full model check.
