# mops find

Generated from `mops find --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Search editable MPS project sources or loaded concept hierarchies.

```text
Usage: mops find [-h] [COMMAND]
Search editable MPS project sources or loaded concept hierarchies.
Commands:
  subconcepts   Find subconcepts of one MPS concept.
  instances     Find instances of one MPS concept.
  usages        Find references to one MPS node.
  root-by-name  Find root nodes by name pattern.
  node-by-id    Find every node with a given node ID.
  help          Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops find subconcepts

Find subconcepts of one MPS concept.

```text
Usage: mops find subconcepts [-h] [--concrete] [--direct] [--json] CONCEPT
Find extending concepts and interface implementors in all loaded language
runtimes, including libraries. Excludes the queried concept; prints qualified
names in sorted order. Unloaded languages contribute no results.
Parameters:
      CONCEPT               Concept name to resolve.
Options:
      --concrete            Exclude abstract concepts and interfaces.
      --direct              Keep only immediate superclass or interface
                              relationships.
  -h, --help                Show this help message and exit.
      --json                Print the qualified concept names as JSON.
```

## mops find instances

Find instances of one MPS concept.

```text
Usage: mops find instances [-h] [--exact] [--full-concept] [--json]
                           [--refs-only] [--limit=N] [--named=PATTERN]
                           [--role=ROLE] CONCEPT [[in SCOPE_SEGMENT...]...]
Find instances of one MPS concept, optionally narrowed by `--named` and
`--role` (which AND together with each other and the scope). Searches editable
project sources by default; append `in <scope-segments>` to search a module,
model, or subtree exhaustively, or `in /` for the whole repository. See `mops
explain scope`.
Parameters:
      CONCEPT               The MPS concept to search for: its qualified name
                              (<language>.structure.<ConceptName>), the same
                              name with .structure. dropped, or a bare short
                              name that is unique across loaded languages.
      [[in SCOPE_SEGMENT...]...]
                            Optional search scope clause: the literal `in`
                              followed by navigation-target segments.
Options:
      --exact               Match only nodes whose direct concept is the
                              queried concept.
      --full-concept        Show fully qualified concept names in text output
                              instead of short names.
  -h, --help                Show this help message and exit.
      --json                Print results as JSON.
      --limit=N             Maximum nodes to return. Defaults to 100; 0 means
                              unlimited.
      --named=PATTERN       Keep only instances whose node name matches this
                              Go-to-Node pattern; supports camel-hump and '*'
                              wildcards. See `mops explain name-pattern`.
      --refs-only           Print one serialized node reference per line and
                              nothing else, for piping. Cannot combine with
                              --json; truncation is reported on stderr.
      --role=ROLE           Keep only instances filling this containment role
                              in their parent.
```

## mops find usages

Find references to one MPS node.

```text
Usage: mops find usages [-h] [--full-concept] [--json] [--refs-only]
                        [--limit=N] NODE_REFERENCE | MODEL_TARGET NODE_ID [in
                        SCOPE_SEGMENT...]...
Find references to one MPS node. Searches editable project sources by default;
append `in <scope-segments>` to search a module, model, or subtree
exhaustively, or `in /` for the whole repository. See `mops explain scope`.
Parameters:
      NODE_REFERENCE | MODEL_TARGET NODE_ID [in SCOPE_SEGMENT...]...
                            Serialized node reference, or model target followed
                              by node ID, then an optional `in` clause.
Options:
      --full-concept        Show fully qualified concept names in text output
                              instead of short names.
  -h, --help                Show this help message and exit.
      --json                Print usage results as JSON.
      --limit=N             Maximum usages to return. Defaults to 100; 0 means
                              unlimited.
      --refs-only           Print one serialized node reference per line and
                              nothing else, for piping. Cannot combine with
                              --json; truncation is reported on stderr.
```

## mops find root-by-name

Find root nodes by name pattern.

```text
Usage: mops find root-by-name [-h] [--full-concept] [--json] [--refs-only]
                              [--limit=N] PATTERN [[in SCOPE_SEGMENT...]...]
Find root nodes by name using MPS Go-to-Node pattern matching. Searches
editable project sources by default; append `in <scope-segments>` to search a
module, model, or the whole repository (`in /`). Only root-bearing scopes are
valid. See `mops explain name-pattern` and `mops explain scope`.
Parameters:
      PATTERN               Name pattern; supports camel-hump and '*'
                              wildcards. See `mops explain name-pattern`.
      [[in SCOPE_SEGMENT...]...]
                            Optional search scope clause: the literal `in`
                              followed by navigation-target segments.
Options:
      --full-concept        Show fully qualified concept names in text output
                              instead of short names.
  -h, --help                Show this help message and exit.
      --json                Print results as JSON.
      --limit=N             Maximum nodes to return. Defaults to 100; 0 means
                              unlimited.
      --refs-only           Print one serialized node reference per line and
                              nothing else, for piping. Cannot combine with
                              --json; truncation is reported on stderr.
```

## mops find node-by-id

Find every node with a given node ID.

```text
Usage: mops find node-by-id [-h] [--full-concept] [--json] [--refs-only]
                            [--limit=N] ID [[in SCOPE_SEGMENT...]...]
Find every node with a given node ID. A node ID is unique only within its
model, so this reports one match per model that holds it, each with its full
node reference — never guessing one over another. The ID accepts either
spelling: the decimal form mops prints, or the encoded form persisted in .mps
files. Searches editable project sources by default; append `in
<scope-segments>` to search a module, model, node subtree, or the whole
repository (`in /`). See `mops explain node-ref` and `mops explain scope`.
Parameters:
      ID                    Node ID in either spelling: the decimal form mops
                              prints or the encoded form from .mps files.
      [[in SCOPE_SEGMENT...]...]
                            Optional search scope clause: the literal `in`
                              followed by navigation-target segments.
Options:
      --full-concept        Show fully qualified concept names in text output
                              instead of short names.
  -h, --help                Show this help message and exit.
      --json                Print results as JSON.
      --limit=N             Maximum nodes to return. Defaults to 100; 0 means
                              unlimited.
      --refs-only           Print one serialized node reference per line and
                              nothing else, for piping. Cannot combine with
                              --json; truncation is reported on stderr.
```
