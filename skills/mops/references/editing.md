# Editing: choose an approach

Start with navigation and search to obtain stable references. Replace the uppercase placeholders in examples with
references copied from mops output.

| Task                                                      | Read                               |
| --------------------------------------------------------- | ---------------------------------- |
| Create modules or models                                  | `mops examples editing.creation`   |
| Traverse and edit native nodes in Groovy                  | `mops examples editing.nodes`      |
| Retarget references and maintain model imports            | `mops examples editing.references` |
| Insert Java as BaseLanguage nodes                         | `mops examples editing.java`       |
| Copy, rename, or change nodes with constraint diagnostics | `mops examples editing.json`       |
| Build, check, and run tests after editing                 | `mops examples validation`         |

Use JSON batches for explicit edit operations with constraint diagnostics; use Code Mode for traversal and custom logic.
Resolve and validate targets before mutations. `mops code help Project.command` describes what a Code Mode command saves
and what it reverts when it throws. A constraint check does not replace a model check, build, or regression test.
