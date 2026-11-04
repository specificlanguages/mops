# mops edit

Generated from `mops edit --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Edit MPS project data.

```text
Usage: mops edit [-h] [COMMAND]
Edit MPS project data.
Commands:
  model  Apply a JSON batch of edit operations.
  help   Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops edit model

Apply a JSON batch of edit operations.

```text
Usage: mops edit model [-h] [--constraints=MODE] [--file=PATH]
Apply a JSON batch of edit operations.
Options:
      --constraints=MODE    How to enforce constraints: advisory (evaluate,
                              report, and apply anyway), best-effort (block on
                              violations and save nothing, warn about concepts
                              that could not be checked; default), or strict
                              (fail on violations or on a concept that could
                              not be checked).
      --file=PATH           Read the edit batch from a JSON file instead of
                              stdin.
  -h, --help                Show this help message and exit.

An operation that names a concept resolves it the same way `mops find
instances` does: a dropped `.structure.` infix is forgiven, and a name that
does not resolve fails the batch, changing nothing, with the same diagnosis
(unknown language, unloaded language, or a "did you mean" among a loaded
language's concepts).

Operation reference: mops explain edit
```
