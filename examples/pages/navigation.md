# Navigate and read

Navigation uses separate space-delimited segments. Model names include stereotypes; a `.suffix` segment is relative to
the preceding module. Use references to disambiguate names. `list` follows containment, not reference links.

| Task                                      | Command          |
| ----------------------------------------- | ---------------- |
| List project modules                      | `{{example-1}}`  |
| Include platform/library modules          | `{{example-2}}`  |
| List a module's models                    | `{{example-3}}`  |
| List model roots                          | `{{example-4}}`  |
| Inspect a root and its children           | `{{example-5}}`  |
| Inspect a node using its stable reference | `{{example-6}}`  |
| Show all children without truncation      | `{{example-7}}`  |
| Count child roles/concepts                | `{{example-8}}`  |
| List one containment role                 | `{{example-9}}`  |
| Export a node subtree as JSON             | `{{example-10}}` |
| Include its ancestry                      | `{{example-11}}` |
| Resolve an ID within a model              | `{{example-12}}` |
| Read the default editor's text            | `{{example-13}}` |
| Render with unloaded languages            | `{{example-14}}` |
| Emit clickable MPS URLs in text output    | `{{example-15}}` |

`list --depth` ranges from 0 to 8; the default is 1. `--summary` cannot be combined with `--depth`. Rendering is a
reading aid, not a serialization that can be edited and imported back.
