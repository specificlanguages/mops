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

The default `strict` constraint mode blocks a batch with violations; `advisory` reports them and applies the batch. A
concept whose language did not load cannot be checked and fails the batch unless you pass `--ignore-missing-languages`,
which skips it with a warning. Constraint checks are distinct from a full model check.
