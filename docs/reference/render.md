# mops render

Generated from `mops render --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are
listed in the [index](index.md).

Render MPS project data.

```text
Usage: mops render [-h] [COMMAND]
Render MPS project data.
Commands:
  node  Render one MPS node as the plain text of its default editor.
  help  Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops render node

Render one MPS node as the plain text of its default editor.

```text
Usage: mops render node [OPTIONS] NODE_REFERENCE
       mops render node [OPTIONS] MODEL_TARGET NODE_ID
Render one MPS node as the plain text of its default editor.
Parameters:
      NODE_REFERENCE | MODEL_TARGET NODE_ID
                            Serialized node reference, or model target followed
                              by node ID.
Options:
      --allow-reflective    Render MPS's generic reflective editor when the
                              node's language has not been made, instead of
                              failing.
  -h, --help                Show this help message and exit.
```
