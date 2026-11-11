# mops create

Generated from `mops create --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are
listed in the [index](index.md).

Create a project module or model.

```text
Usage: mops create [-h] [COMMAND]
Create a project module or model through the daemon and register it with the
project. Each module lands under the conventional directory for its kind
(languages/<name>/<name>.mpl, solutions/<name>/<name>.msd, and so on) unless
--descriptor names the descriptor file. --dry-run validates and prints what
would be created without changing the project.
Commands:
  language   Create a language project module.
  solution   Create a solution project module.
  devkit     Create a devkit project module.
  generator  Create a generator for a language.
  model      Create an empty model in a project module.
  help       Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops create language

Create a language project module.

```text
Usage: mops create language [-h] [--dry-run] [--json] [--with-generator]
                            [--descriptor=FILE] MODULE_NAME
Create a language project module.
Parameters:
      MODULE_NAME
Options:
      --descriptor=FILE     Descriptor file to write instead of the
                              conventional location.
      --dry-run             Validate and describe creation without changing the
                              project.
  -h, --help                Show this help message and exit.
      --json                Print the plan or report as JSON.
      --with-generator
```

## mops create solution

Create a solution project module.

```text
Usage: mops create solution [-h] [--dry-run] [--json] [--descriptor=FILE]
                            [--usage-preset=PRESET] MODULE_NAME
Create a solution project module.
Parameters:
      MODULE_NAME
Options:
      --descriptor=FILE     Descriptor file to write instead of the
                              conventional location.
      --dry-run             Validate and describe creation without changing the
                              project.
  -h, --help                Show this help message and exit.
      --json                Print the plan or report as JSON.
      --usage-preset=PRESET Generation-target facets for the solution's
                              intended use: not-generated (no facets, models
                              are only analyzed), text (generates plain text),
                              java (generates Java compiled and loaded by MPS),
                              java-tests (java plus the tests facet),
                              java-mps-plugin (java whose classes extend MPS as
                              a plugin). Default: not-generated.

The preset configures facets only; dependencies, used languages, devkits, and
model contents are separate steps. Both the plan and the report name the preset
applied and list every facet it produced, so the default is visible in the
output.
```

## mops create devkit

Create a devkit project module.

```text
Usage: mops create devkit [-h] [--dry-run] [--json] [--descriptor=FILE]
                          MODULE_NAME
Create a devkit project module.
Parameters:
      MODULE_NAME
Options:
      --descriptor=FILE     Descriptor file to write instead of the
                              conventional location.
      --dry-run             Validate and describe creation without changing the
                              project.
  -h, --help                Show this help message and exit.
      --json                Print the plan or report as JSON.
```

## mops create generator

Create a generator for a language.

```text
Usage: mops create generator [-h] [--dry-run] [--json] [--standalone]
                             --alias=<alias> [--descriptor=FILE]
                             --language=<language>
Create a generator for a language.
Options:
      --alias=<alias>
      --descriptor=FILE     Descriptor file to write instead of the
                              conventional location. Requires --standalone.
      --dry-run             Validate and describe creation without changing the
                              project.
  -h, --help                Show this help message and exit.
      --json                Print the plan or report as JSON.
      --language=<language>
      --standalone
```

## mops create model

Create an empty model in a project module.

```text
Usage: mops create model [-h] [--dry-run] [--file-per-root] [--json]
                         --module=MODULE MODEL_NAME
Create an empty model in a project module.
The model gets the devkits and used languages that MPS gives a new model in
that module, such as the aspect devkit for a language's structure model.
Parameters:
      MODEL_NAME
Options:
      --dry-run             Validate and describe creation without changing the
                              project.
      --file-per-root       Use file-per-root XML persistence.
  -h, --help                Show this help message and exit.
      --json                Print the plan or report as JSON.
      --module=MODULE
```
