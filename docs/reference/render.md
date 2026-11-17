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
Usage: mops render node [OPTIONS] NODE_TARGET
       mops render node [OPTIONS] MODEL_TARGET NODE_ID
Render one MPS node as the plain text of its default editor.
Parameters:
      NODE_TARGET | MODEL_TARGET NODE_ID
                            Node target as one string: <model>/<node ID> (model
                              name or serialized model reference; decimal or
                              encoded node ID) or a serialized node reference.
                              Or a model target followed by a node ID.
Options:
      --allow-reflective    Render MPS's generic reflective editor when the
                              node's language has not been made, instead of
                              failing.
  -h, --help                Show this help message and exit.

Prints the node the way it appears in the MPS editor, with the editor's line
breaks and indentation and trailing whitespace trimmed from each line. Any node
is renderable, not only root nodes. The output is a quick overview for reading,
not a serialization that can be imported back. The target is addressed the same
way as in `mops get node`, and fails the same way when it does not resolve.

If a concept in the node's subtree does not resolve because its language is not
loaded, the command fails with LANGUAGE_NOT_LOADED. It diagnoses each unloaded
language the same way `find instances` does, naming each root cause (not built,
absent, or a broken dependency) with the fix that mops knows for it, such as
`mops make module <language>`, and points at `mops diagnose module <language>`.
A concept whose language is loaded but defines no editor is not an error: MPS
renders it with its generic reflective editor (concept aliases and roles rather
than the language's own notation), and the command prints that.
```
