# Search

Default searches cover editable project sources. An explicit `in` scope can include library/platform content.
`find instances` includes subconcepts by default; `--exact` restricts it to the direct concept.

<!-- markdownlint-disable MD013 -->

| Task                                            | Command          |
| ----------------------------------------------- | ---------------- |
| Find class nodes                                | `{{example-1}}`  |
| Find only direct instances                      | `{{example-2}}`  |
| Find named roots by camel-hump/wildcard pattern | `{{example-3}}`  |
| Search roots in one model                       | `{{example-4}}`  |
| Search roots across the repository              | `{{example-5}}`  |
| Find references to a node                       | `{{example-6}}`  |
| Find all matching node IDs                      | `{{example-7}}`  |
| Get machine-readable matches                    | `{{example-8}}`  |
| Return every match as a reference               | `{{example-9}}`  |
| Render every matching root                      | `{{example-10}}` |

<!-- markdownlint-enable MD013 -->

`find` defaults to 100 matches; `--limit 0` removes the cap. `--refs-only` cannot be combined with `--json`. Use
`{{example-11}}` for pattern matching rules.
