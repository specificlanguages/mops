# Code Mode: run compact Groovy programs

Save a snippet as `task.groovy` and run `{{example-22}}`, or pass it on stdin:

```sh
{{example-24}}
```

The following snippets are complete programs. `NODE_REF`, `CLASS_REF`, `TARGET_REF`, and `STATEMENTS_REF` are
placeholders. Use a method node for the `body` child examples and a BaseLanguage `ClassifierType` node for the
`classifier` reference examples. Reads and lookups belong in `project.read`; writes belong in `project.command`. Blocks
cannot nest. Successful commands save changes; exceptions do not roll back partial changes. Each invocation has fresh
variables. Return useful data: strings print as text, maps/lists become JSON, and native nodes/models/modules become
serialized references.

<!-- markdownlint-disable MD013 -->

| Task                            | Groovy program              |
| ------------------------------- | --------------------------- |
| List root names and references  | `{{root-names}}`            |
| Read a node property            | `{{property}}`              |
| Find a root with an exact name  | `{{find-root}}`             |
| List containment descendants    | `{{descendants}}`           |
| List ancestors                  | `{{ancestors}}`             |
| List available property names   | `{{property-names}}`        |
| List child roles                | `{{child-roles}}`           |
| List reference roles            | `{{reference-roles}}`       |
| Read one child                  | `{{single-child}}`          |
| Read ordered children in a role | `{{ordered-children}}`      |
| Resolve a reference target      | `{{reference-target}}`      |
| Rename a node                   | `{{rename}}`                |
| Change a reference target       | `{{retarget}}`              |
| Clear a child role              | `{{clear-child}}`           |
| Reverse ordered members         | `{{reverse-members}}`       |
| Create a class root             | `{{create-root}}`           |
| Create a solution and model     | `{{create-solution-model}}` |
| Render a node                   | `{{render}}`                |
| Inspect a runtime object's API  | `{{runtime-help}}`          |

<!-- markdownlint-enable MD013 -->

`child[role]` rejects multiple children; use `children[role]` for lists. Newly attached children must be detached.
Indexed accessors shadow native Groovy getter properties. Use concept descriptors and native methods when necessary;
consult `{{example-23}}` for signatures. Code Mode child assignments do not check containment constraints.

For streaming searches with custom result data:

<!-- markdownlint-disable MD013 -->

```groovy
{{instances}}
```

<!-- markdownlint-enable MD013 -->

<!-- markdownlint-disable MD013 -->

```groovy
{{usages}}
```

<!-- markdownlint-enable MD013 -->
