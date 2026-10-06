# JSON edits

Use batches when you want explicit edit operations and constraint diagnostics. This one-liner renames a node:

```sh
printf '%s\n' '{"operations":[{"op":"setProperty","target":"NODE_REF","name":"name","value":"Renamed"}]}' | mops edit model
```

Copy an existing test/root into another model and rename the copy in the same batch (`MODEL_REF` is the destination
model's serialized reference). Single shell quotes preserve the batch-local `$copy` alias:

```sh
printf '%s\n' '{"operations":[{"op":"copyAsRoot","model":"MODEL_REF","source":"SOURCE_REF","as":"copy"},{"op":"setProperty","target":"$copy","name":"name","value":"RegressionTest"}]}' | mops edit model
```

Copy a member subtree into a class, preserving its references and assigning fresh node IDs:

```sh
printf '%s\n' '{"operations":[{"op":"copyAsChild","target":"CLASS_REF","source":"MEMBER_REF","role":"member","position":"last"}]}' | mops edit model
```

| Task                                        | Command                                                    |
| ------------------------------------------- | ---------------------------------------------------------- |
| Apply a saved batch                         | `mops edit model --file edits.json`                        |
| Require all concepts to be checkable        | `mops edit model --file edits.json --constraints strict`   |
| Apply while reporting constraint violations | `mops edit model --file edits.json --constraints advisory` |
| Discover subtree creation notation          | `mops explain edit.addRoot`                                |
| Discover replacement notation               | `mops explain edit.replace`                                |
| Discover wrapping notation                  | `mops explain edit.wrap`                                   |
| Discover copying notation                   | `mops explain edit.copyAsChild`                            |
| Export the batch schema                     | `mops explain --schema edit`                               |

The default `best-effort` constraint mode blocks violations but warns and skips checking concepts whose languages are
unloaded. Constraint checks are distinct from a full model check.
