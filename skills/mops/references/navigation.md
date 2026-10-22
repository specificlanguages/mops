# Navigate and read

Navigation uses separate space-delimited segments. Model names include stereotypes. A `.suffix` segment is relative to
the preceding module and is only the part after the module name: `.main` after `sample.solution` means
`sample.solution.main`. A bare `.` segment names the model whose name equals the module name (`sample.app .` is the
model `sample.app` of module `sample.app`). Use references to disambiguate names; a lone `sample.app` is ambiguous when
a module and a model share that name. `list` follows containment, not reference links.

| Task                                                  | Command                                             |
| ----------------------------------------------------- | --------------------------------------------------- |
| List project modules                                  | `mops list`                                         |
| List all project modules without truncation           | `mops list --limit 0`                               |
| Include platform/library modules                      | `mops list /`                                       |
| List a module's models                                | `mops list sample.solution`                         |
| List model roots                                      | `mops list sample.solution .main`                   |
| List roots of the model named like its module         | `mops list sample.app .`                            |
| Inspect a root and its children                       | `mops list sample.solution .main Example --depth 2` |
| Inspect a node using its stable reference             | `mops list 'NODE_REF' --depth 2`                    |
| Show all children without truncation                  | `mops list 'NODE_REF' --limit 0`                    |
| Count child roles/concepts                            | `mops list 'NODE_REF' --summary`                    |
| List one containment role                             | `mops list 'NODE_REF' --role member`                |
| Export a node subtree as JSON                         | `mops get node 'NODE_REF'`                          |
| Include its containment chain up to the root          | `mops get node --ancestry 'NODE_REF'`               |
| Summarize a node: name, parent, child counts per role | `mops get node --summary 'NODE_REF'`                |
| Export one level, deeper nodes as stubs               | `mops get node --depth 1 'NODE_REF'`                |
| Resolve an ID within a model                          | `mops get node sample.model 123456789`              |
| Locate a module's descriptor, jars, and sources       | `mops get module sample.solution`                   |
| Read the default editor's text                        | `mops render node 'NODE_REF'`                       |
| Render with unloaded languages                        | `mops render node --allow-reflective 'NODE_REF'`    |
| Emit clickable MPS URLs in text output                | `mops --refs-as-urls list sample.solution .main`    |

`list --depth` ranges from 0 to 8; the default is 1. Each level shows up to 50 children and ends in an indented
`# truncated: ...` metadata line when more exist; `--limit 0` lifts the cap, which matters when listing project modules.
`--summary` cannot be combined with `--depth`. `get node --ancestry` adds the containment chain from the node up to its
root. `get node` prints the whole subtree unless `--depth N` or `--summary` bounds it; a `truncatedAt` field marks
a bounded export, and stubs below the depth carry a `reference` to fetch next. Pass `--compact` for single-line JSON.
Rendering is a reading aid, not a serialization that can be edited and imported back; the default editor can
leave information out, so inspect the subtree with `list --depth` or `get node` when a rendered view is not enough.
