# mops

`mops` is a small helper CLI for inspecting JetBrains MPS artifacts.

The current command is `mops decompress`, which expands compressed MPS persistence v9 model XML so the model is easier to inspect with humans or LLMs. It rewrites selected node-graph attributes using the model's own `<registry>` and `<imports>` sections:

- `node@concept` becomes the full concept name.
- `node@role`, `property@role`, and `ref@role` become unqualified role names.
- regular MPS node IDs in `node@id`, `ref@node`, and `ref@to` become signed decimal Java `long` values.
- import aliases in `ref@to` become the full imported model reference.

The `<registry>` subtree is left semantically unchanged.

## Non-goals

The output is for inspection only. It is well-formed XML, but it is not intended to be loaded back into MPS or round-tripped as normal MPS persistence.

There is no in-place mode. Output always goes to stdout.

## Usage

```sh
mops decompress < input.mps > output.mps
mops decompress input.mps > output.mps
mops list-models
mops --version
```

Diagnostics are written to stderr. Unsupported persistence versions, missing registries, malformed XML, and duplicate registry/import indices fail with a non-zero exit code.

## Commands

```sh
mops decompress [input.mps]
```

Reads from stdin when `input.mps` is omitted. Writes transformed XML to stdout.

```sh
mops list-models [root]
```

Scans `root`, or the current directory when omitted, for standalone `.mps` model files and file-per-root `.model` metadata files. Prints a pretty JSON object mapping model IDs to absolute paths. When multiple locations have the same model ID, the value is an array of paths instead of a string.

File-per-root models are reported as the parent folder of the `.model` file with a trailing `/`. Paths are normalized to `/` separators.

When possible, `list-models` uses `git ls-files -co --exclude-standard` so Git-ignored files are skipped. If Git is unavailable or the root is not in a Git worktree, it falls back to a filesystem walk and only skips `.git`.

## Build And Test

```sh
go test ./...
go build ./cmd/mops
```

## Attribution

The regular node ID decoding follows the behavior of JetBrains MPS persistence v9 `IdEncoder` and `JavaFriendlyBase64`.

- https://github.com/JetBrains/MPS/blob/9b2eefc0208fecedb3e08c7d9f7f53aa22b5e72e/core/persistence/source/jetbrains/mps/smodel/persistence/def/v9/IdEncoder.java
- https://github.com/JetBrains/MPS/blob/9b2eefc0208fecedb3e08c7d9f7f53aa22b5e72e/core/smodel/source/jetbrains/mps/smodel/JavaFriendlyBase64.java
