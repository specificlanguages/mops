# mops list

Generated from `mops list --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

List an MPS navigation target as a bounded tree.

```text
Usage: mops list [-h] [--full-concept] [--json] [--summary] [--depth=N]
                 [--limit=N] [--role=ROLE] [TARGET_SEGMENT...]
List an MPS navigation target as a bounded tree.
Parameters:
      [TARGET_SEGMENT...]   MPS navigation target segments. Omit for project
                              root; use / for repository root.
Options:
      --depth=N             Maximum descendant depth to include, from 0 to 8.
                              Defaults to 1. Not allowed with --summary.
      --full-concept        Show fully qualified concept names in text output
                              instead of short names.
  -h, --help                Show this help message and exit.
      --json                Print the semantic list tree as JSON.
      --limit=N             Maximum children to show per level. Defaults to 50;
                              0 means unlimited.
      --role=ROLE           Show only the target node's children in this
                              containment role. Valid only for a node target.
      --summary             Print grouped counts of the target's children
                              instead of enumerating them. Not allowed with
                              --depth.
```
