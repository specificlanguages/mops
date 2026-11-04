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

Space-separated target segments address a module, model, or node. After a
module, a `.suffix` model segment expands against the module name, and a
segment that is a single `.` names the model whose name equals the module name;
that name alone is ambiguous between the module and the model.

Text output is one indented tab-separated row per entry, tagged by kind
(`project`, `repository`, `module`, `model`, `root`, or `node`); --json prints
the semantic tree. A level wider than --limit ends in an indented `# truncated:
showing <shown> of <total> children; pass --limit 0 for all` line. See `mops
explain output`.

--summary groups the target's children: per role, with the dominant concepts,
for a node; per concept for a model's roots; per model for a module; per kind
for the project or repository.
```
