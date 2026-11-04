# mops get

Generated from `mops get --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Get MPS project data.

```text
Usage: mops get [-h] [COMMAND]
Get MPS project data.
Commands:
  node    Export one MPS node as JSON.
  module  Report where MPS locates one module's files.
  help    Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops get node

Export one MPS node as JSON.

```text
Usage: mops get node [OPTIONS] NODE_REFERENCE
       mops get node [OPTIONS] MODEL_TARGET NODE_ID
Export one MPS node as JSON.
Parameters:
      NODE_REFERENCE | MODEL_TARGET NODE_ID
                            Serialized node reference, or model target followed
                              by node ID.
Options:
      --ancestry            Nest the node's full containment chain up to the
                              root node, not just its immediate parent.
      --compact             Print single-line JSON instead of the
                              pretty-printed default.
      --depth=N             Export N containment levels below the node in full
                              and the nodes at level N as stubs (role, concept,
                              name, reference, childCount); 0 leaves the
                              children out. Default is the whole subtree. Not
                              allowed with --summary.
  -h, --help                Show this help message and exit.
      --summary             Print only the node's type, name, concept,
                              reference, parent, and the number of children per
                              role. Not allowed with --depth.
```

## mops get module

Report where MPS locates one module's files.

```text
Usage: mops get module [-h] [--json] MODULE
Report where MPS locates one module's descriptor, jars, class output, and model
sources.
Parameters:
      MODULE                Module name or serialized module reference; library
                              modules included.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the module's locations as JSON.
```
