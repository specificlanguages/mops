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
Usage: mops make module [-h] [--json] MODULE...
Make the named modules and their transitive dependency closure, so an un-made
dependency is made too.
Parameters:
      MODULE...             Module name or serialized module reference.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the make result as JSON.

Text output prints the make messages as `<kind><TAB><text>` lines on stderr,
since they are diagnostics, and a one-line action report on stdout: `made <N>
module(s): success`, `made <N> module(s): FAILED (<N> error(s))`, or `nothing
to generate`. The command exits non-zero when the make reports errors.
```

## mops make project

Make every generatable module in the project.

```text
Usage: mops make project [-h] [--json]
Make every generatable module in the project.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the make result as JSON.

Text output prints the make messages as `<kind><TAB><text>` lines on stderr,
since they are diagnostics, and a one-line action report on stdout: `made <N>
module(s): success`, `made <N> module(s): FAILED (<N> error(s))`, or `nothing
to generate`. The command exits non-zero when the make reports errors.
```
