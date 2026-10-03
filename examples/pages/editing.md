# Editing: choose an approach

Start with navigation and search to obtain stable references. Replace the uppercase placeholders in examples with
references copied from mops output.

| Task                                                      | Read            |
| --------------------------------------------------------- | --------------- |
| Create modules or models                                  | `{{example-1}}` |
| Traverse and edit native nodes in Groovy                  | `{{example-2}}` |
| Retarget references and maintain model imports            | `{{example-3}}` |
| Insert Java as BaseLanguage nodes                         | `{{example-4}}` |
| Copy, rename, or change nodes with constraint diagnostics | `{{example-5}}` |
| Build, check, and run tests after editing                 | `{{example-6}}` |

Use JSON batches for explicit edit operations with constraint diagnostics; use Code Mode for traversal and custom logic.
Resolve and validate targets before mutations. Code Mode commands save on success, but do not roll back partial edits on
an exception. A constraint check does not replace a model check, build, or regression test.
