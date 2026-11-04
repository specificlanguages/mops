# mops explain

Generated from `mops explain --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are
listed in the [index](index.md).

Explain mops notations such as node references and edit batches.

```text
Usage: mops explain [-h] [--schema] [PATH]
Explain mops notations (the textual formats mops exchanges with agents). The
command is offline: it never starts a daemon, resolves a project root, or needs
an MPS home. An unknown path exits non-zero with sibling suggestions.

Topics:
  code            execute Groovy code over an open MPS project
  edit            the edit-batch notation: an ordered batch of edit operations
                  applied as a unit,
  inline-subtree  the node-tree an edit operation builds: fresh nodes, move
                  leaves, and copy leaves.
  target          how an edit operation names a node.
  position        where a child sits among the children of a containment role.
  node-ref        the node reference notation.
  name-pattern    the Name Pattern notation for `mops find root-by-name`.
  scope           the search scope clause on `mops find`.
  output          how command output separates results, metadata, and
                  diagnostics.
Parameters:
      [PATH]                Topic dot-path, e.g. edit or edit.copyAsChild. Omit
                              to list topics.
Options:
  -h, --help                Show this help message and exit.
      --schema              Print the generated JSON Schema for the edit-batch
                              notation (only with the edit topic).
```
