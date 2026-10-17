# Navigate and read

Navigation uses separate space-delimited segments. Model names include stereotypes; a `.suffix` segment is relative to
the preceding module. Use references to disambiguate names. `list` follows containment, not reference links.

| Task                                            | Command                                             |
| ----------------------------------------------- | --------------------------------------------------- |
| List project modules                            | `mops list`                                         |
| Include platform/library modules                | `mops list /`                                       |
| List a module's models                          | `mops list sample.solution`                         |
| List model roots                                | `mops list sample.solution .main`                   |
| Inspect a root and its children                 | `mops list sample.solution .main Example --depth 2` |
| Inspect a node using its stable reference       | `mops list 'NODE_REF' --depth 2`                    |
| Show all children without truncation            | `mops list 'NODE_REF' --limit 0`                    |
| Count child roles/concepts                      | `mops list 'NODE_REF' --summary`                    |
| List one containment role                       | `mops list 'NODE_REF' --role member`                |
| Export a node subtree as JSON                   | `mops get node 'NODE_REF'`                          |
| Include its ancestry                            | `mops get node --ancestry 'NODE_REF'`               |
| Resolve an ID within a model                    | `mops get node sample.model 123456789`              |
| Locate a module's descriptor, jars, and sources | `mops get module sample.solution`                   |
| Read the default editor's text                  | `mops render node 'NODE_REF'`                       |
| Render with unloaded languages                  | `mops render node --allow-reflective 'NODE_REF'`    |
| Emit clickable MPS URLs in text output          | `mops --refs-as-urls list sample.solution .main`    |

`list --depth` ranges from 0 to 8; the default is 1. Each level shows up to 50 children and ends in an indented
`# truncated: ...` metadata line when more exist; `--limit 0` lifts the cap. `--summary` cannot be combined with
`--depth`. Rendering is a reading aid, not a serialization that can be edited and imported back.
