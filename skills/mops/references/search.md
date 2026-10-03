# Search

Default searches cover editable project sources. An explicit `in` scope can include library/platform content.
`find instances` includes subconcepts by default; `--exact` restricts it to the direct concept.

<!-- markdownlint-disable MD013 -->

| Task                                            | Command                                                                                                                |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Find class nodes                                | `mops find instances jetbrains.mps.baseLanguage.ClassConcept`                                                          |
| Find only direct instances                      | `mops find instances --exact jetbrains.mps.baseLanguage.ClassConcept`                                                  |
| Find named roots by camel-hump/wildcard pattern | `mops find root-by-name '*Builder*'`                                                                                   |
| Search roots in one model                       | `mops find root-by-name '*Builder*' in sample.solution .main`                                                          |
| Search roots across the repository              | `mops find root-by-name '*Builder*' in /`                                                                              |
| Find references to a node                       | `mops find usages 'NODE_REF'`                                                                                          |
| Find all matching node IDs                      | `mops find node-by-id 123456789 in /`                                                                                  |
| Get machine-readable matches                    | `mops find instances --json jetbrains.mps.baseLanguage.ClassConcept`                                                   |
| Return every match as a reference               | `mops find root-by-name --refs-only --limit 0 '*Builder*'`                                                             |
| Render every matching root                      | `mops find root-by-name --refs-only --limit 0 '*Builder*' \| while IFS= read -r ref; do mops render node "$ref"; done` |

<!-- markdownlint-enable MD013 -->

`find` defaults to 100 matches; `--limit 0` removes the cap. `--refs-only` cannot be combined with `--json`. Use
`mops explain name-pattern` for pattern matching rules.
