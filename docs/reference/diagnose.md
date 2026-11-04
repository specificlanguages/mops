# mops diagnose

Generated from `mops diagnose --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are
listed in the [index](index.md).

Diagnose how the daemon loaded the MPS project.

```text
Usage: mops diagnose [-h] [COMMAND]
Diagnose how the daemon loaded the MPS project.
Commands:
  module   Diagnose the load state of one module.
  project  Report which project languages and Java-bearing modules loaded.
  help     Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops diagnose module

Diagnose the load state of one module.

```text
Usage: mops diagnose module [-h] [--json] MODULE
Diagnose the load state of one module, addressed by name or serialized module
reference.
Exits 1 when the module failed to load (a blocking problem, including an absent
module), 0 when it is loaded.
An informational problem, such as a missing Java facet on a non-language
module, does not fail.
Parameters:
      MODULE                Module name or serialized module reference.
Options:
  -h, --help                Show this help message and exit.
      --json                Print the full diagnosis as JSON.
```

## mops diagnose project

Report which project languages and Java-bearing modules loaded.

```text
Usage: mops diagnose project [-h] [--all] [--json]
Report which project languages and Java-bearing modules loaded, and why each
unloaded one failed.
Options:
      --all                 Also list modules that loaded, not just the failed
                              ones.
  -h, --help                Show this help message and exit.
      --json                Print the full diagnosis as JSON.
```
