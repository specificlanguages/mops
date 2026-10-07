# Code Mode: run compact Groovy programs

Save a snippet as `task.groovy` and run `{{run-file}}`, or pass it on stdin:

```sh
{{run-stdin}}
```

The following snippets are complete programs. `NODE_REF`, `CLASS_REF`, `TARGET_REF`, and `STATEMENTS_REF` are
placeholders. Use a method node for the `body` child examples and a BaseLanguage `ClassifierType` node for the
`classifier` reference examples. Reads and lookups belong in `project.read`; writes belong in `project.command`. Blocks
cannot nest. Successful commands save changes; exceptions do not roll back partial changes. Each invocation has fresh
variables. Return useful data: strings print as text, maps/lists become JSON, and native nodes/models/modules become
serialized references.

{{table:nodes|Groovy program}}

`child[role]` rejects multiple children; use `children[role]` for lists. Newly attached children must be detached.
Indexed accessors shadow native Groovy getter properties. Use concept descriptors and native methods when necessary;
consult `{{node-api}}` for signatures. Code Mode child assignments do not check containment constraints.

For streaming searches with custom result data:

```groovy
{{instances}}
```

```groovy
{{usages}}
```
