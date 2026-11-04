# mops make

Generated from `mops make --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Generate and compile modules or the whole project.

```text
Usage: mops make [-h] [COMMAND]
Run the MPS make (generation and compilation) on modules or the whole project.
Commands:
  module   Make the named modules and their dependency closure.
  project  Make every generatable module in the project.
  help     Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops make module

Make the named modules and their dependency closure.

```text
Usage: mops make module [-h] [--json] [--threads=N] MODULE...
Make the named modules and their transitive dependency closure, so an un-made
dependency is made too.
Parameters:
      MODULE...             Module name or serialized module reference.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the make result as JSON.
      --threads=N           Generate with N threads during this make; 1
                              generates sequentially. Without it, the make uses
                              the daemon's MPS generation settings (MPS's
                              default is sequential).

Text output prints the make messages as `<kind><TAB><text>` lines on stderr,
since they are diagnostics, and a one-line action report on stdout: `made <N>
module(s): success`, `made <N> module(s): FAILED (<N> error(s))`, or `nothing
to generate`. The command exits non-zero when the make reports errors.

With `--threads`, MPS generates in parallel within each model (it applies
template rules in parallel), not across models or modules, so the gain depends
on the size of the models. MPS generates in parallel only in strict mode, so
with N above 1 the make fails when the generation settings turn strict mode off.
```

## mops make project

Make every generatable module in the project.

```text
Usage: mops make project [-h] [--json] [--threads=N]
Make every generatable module in the project.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the make result as JSON.
      --threads=N           Generate with N threads during this make; 1
                              generates sequentially. Without it, the make uses
                              the daemon's MPS generation settings (MPS's
                              default is sequential).

Text output prints the make messages as `<kind><TAB><text>` lines on stderr,
since they are diagnostics, and a one-line action report on stdout: `made <N>
module(s): success`, `made <N> module(s): FAILED (<N> error(s))`, or `nothing
to generate`. The command exits non-zero when the make reports errors.

With `--threads`, MPS generates in parallel within each model (it applies
template rules in parallel), not across models or modules, so the gain depends
on the size of the models. MPS generates in parallel only in strict mode, so
with N above 1 the make fails when the generation settings turn strict mode off.
```
