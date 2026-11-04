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

It covers modules that `diagnose project` does not list: those without a Java
facet and those absent from the repository. Text output is a header row
(`module`, kind, `present=<bool>`, `loaded=<bool>`) followed by the module's
load-problem tree, indented by depth so a dependency chain reads from the
module down to its root causes. --json prints the structured diagnosis. The
exit code and the diagnosis are the same in text and --json modes.

Reason codes for an unloaded module: ABSENT (not in the repository),
NOT_A_MODULE (resolves to something that does not load classes), NO_JAVA_FACET
(no Java facet; a defect for a language, informational for another module),
CLASSES_DISABLED (the Java facet is configured not to load classes), NOT_BUILT
(classes not generated or compiled yet), BROKEN_DEPENDENCIES (blocked by
depended-on modules, reported recursively), and RUNTIME_LOAD_FAILED (everything
present but the runtime still did not register; usually a class-link or version
error in the daemon log).
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

Use it to trace a CONCEPT_NOT_FOUND from `find instances` to its cause: a
concept resolves by name only if its owning language's runtime is loaded.

Text output starts with a `modules<TAB><loaded>/<total> loaded<TAB><failed>
failed` summary row, which is a result row, not metadata. One tab-separated row
follows per failed module: its name, kind, and reason code; for
BROKEN_DEPENDENCIES, indented rows name the root-cause modules to fix. A final
`#` metadata line says that modules without a Java facet are not listed;
inspect them with `mops diagnose module`. --json prints the full structured
diagnosis. The command is a listing and exits 0 regardless of failed modules.

Reason codes for an unloaded module: ABSENT (not in the repository),
NOT_A_MODULE (resolves to something that does not load classes), NO_JAVA_FACET
(no Java facet; a defect for a language, informational for another module),
CLASSES_DISABLED (the Java facet is configured not to load classes), NOT_BUILT
(classes not generated or compiled yet), BROKEN_DEPENDENCIES (blocked by
depended-on modules, reported recursively), and RUNTIME_LOAD_FAILED (everything
present but the runtime still did not register; usually a class-link or version
error in the daemon log).
```
