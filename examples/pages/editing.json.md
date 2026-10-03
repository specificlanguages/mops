# JSON edits

Use batches when you want explicit edit operations and constraint diagnostics. This one-liner renames a node:

<!-- markdownlint-disable MD013 -->

```sh
{{example-9}}
```

<!-- markdownlint-enable MD013 -->

Copy an existing test/root into another model and rename the copy in the same batch (`MODEL_REF` is the destination
model's serialized reference). Single shell quotes preserve the batch-local `$copy` alias:

<!-- markdownlint-disable MD013 -->

```sh
{{example-10}}
```

<!-- markdownlint-enable MD013 -->

Copy a member subtree into a class, preserving its references and assigning fresh node IDs:

<!-- markdownlint-disable MD013 -->

```sh
{{example-11}}
```

<!-- markdownlint-enable MD013 -->

| Task                                        | Command         |
| ------------------------------------------- | --------------- |
| Apply a saved batch                         | `{{example-1}}` |
| Require all concepts to be checkable        | `{{example-2}}` |
| Apply while reporting constraint violations | `{{example-3}}` |
| Discover subtree creation notation          | `{{example-4}}` |
| Discover replacement notation               | `{{example-5}}` |
| Discover wrapping notation                  | `{{example-6}}` |
| Discover copying notation                   | `{{example-7}}` |
| Export the batch schema                     | `{{example-8}}` |

The default `best-effort` constraint mode blocks violations but warns and skips checking concepts whose languages are
unloaded. Constraint checks are distinct from a full model check.
