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
Usage: mops make module [-h] [--json] [--[no-]save-transient-models] [--[no-]
                        strict] [--messages=LEVEL]
                        [--performance-tracing=LEVEL] [--threads=N] MODULE...
Make the named modules and their transitive dependency closure, so an un-made
dependency is made too.
Parameters:
      MODULE...             Module name or serialized module reference.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the make result as JSON.
      --messages=LEVEL      The lowest severity of messages the result has,
                              from every make step: error, warning, or info.
                              Without it, the result has errors and warnings
                              but no info messages.
      --performance-tracing=LEVEL
                            Report how long generation takes: off, steps,
                              languages, or types (most detail). The report
                              comes as info messages, so combine it with
                              --messages info.
      --[no-]save-transient-models
                            Keep the intermediate models of each generation
                              step, or not with --no-save-transient-models.
      --[no-]strict         Generate in MPS strict mode, or not with
                              --no-strict. MPS's default is strict.
      --threads=N           Generate with N threads; 1 generates sequentially.
                              MPS's default is sequential.

Text output prints the make messages as `<kind><TAB><text>` lines on stderr,
since they are diagnostics, and a one-line action report on stdout: `made <N>
module(s): success`, `made <N> module(s): FAILED (<N> error(s))`, or `nothing
to generate`. The command exits non-zero when the make reports errors.

The make generates every model of the made modules and deletes files in their
output folders that it no longer generates. It leaves out the models of modules
without generation-target facets, such as solutions created with the
`not-generated` usage preset, and names each such module in a warning.

The generation options apply to this make only; without them the make uses the
daemon's MPS generation settings. With `--threads`, MPS generates in parallel
within each model (it applies template rules in parallel), not across models or
modules, so the gain depends on the size of the models. MPS generates in
parallel only in strict mode, so with N above 1 the make fails when strict mode
is off; `--strict` turns it on. Saved transient models stay in the daemon until
the next make.
```

## mops make project

Make every generatable module in the project.

```text
Usage: mops make project [-h] [--json] [--[no-]save-transient-models] [--[no-]
                         strict] [--messages=LEVEL]
                         [--performance-tracing=LEVEL] [--threads=N]
Make every generatable module in the project.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the make result as JSON.
      --messages=LEVEL      The lowest severity of messages the result has,
                              from every make step: error, warning, or info.
                              Without it, the result has errors and warnings
                              but no info messages.
      --performance-tracing=LEVEL
                            Report how long generation takes: off, steps,
                              languages, or types (most detail). The report
                              comes as info messages, so combine it with
                              --messages info.
      --[no-]save-transient-models
                            Keep the intermediate models of each generation
                              step, or not with --no-save-transient-models.
      --[no-]strict         Generate in MPS strict mode, or not with
                              --no-strict. MPS's default is strict.
      --threads=N           Generate with N threads; 1 generates sequentially.
                              MPS's default is sequential.

Text output prints the make messages as `<kind><TAB><text>` lines on stderr,
since they are diagnostics, and a one-line action report on stdout: `made <N>
module(s): success`, `made <N> module(s): FAILED (<N> error(s))`, or `nothing
to generate`. The command exits non-zero when the make reports errors.

The make generates every model of the made modules and deletes files in their
output folders that it no longer generates. It leaves out the models of modules
without generation-target facets, such as solutions created with the
`not-generated` usage preset, and names each such module in a warning.

The generation options apply to this make only; without them the make uses the
daemon's MPS generation settings. With `--threads`, MPS generates in parallel
within each model (it applies template rules in parallel), not across models or
modules, so the gain depends on the size of the models. MPS generates in
parallel only in strict mode, so with N above 1 the make fails when strict mode
is off; `--strict` turns it on. Saved transient models stay in the daemon until
the next make.
```
