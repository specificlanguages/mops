# mops code

Generated from `mops code --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are listed
in the [index](index.md).

Run trusted Groovy against an open MPS project.

```text
Usage: mops code [-h] [COMMAND]
Run trusted Groovy against an open MPS project.
Commands:
  run   Run a trusted Groovy program from FILE or stdin.
  help  Show the Code Mode extension reference.
Options:
  -h, --help                Show this help message and exit.
```

## mops code run

Run a trusted Groovy program from FILE or stdin.

```text
Usage: mops code run [-h] [--timeout=<timeoutSeconds>] [FILE]
Run a trusted Groovy program. FILE and - both mean stdin when omitted or set to
-.
Parameters:
      [FILE]
Options:
  -h, --help                Show this help message and exit.
      --timeout=<timeoutSeconds>
                            Hard deadline in seconds; 0 disables it (default:
                              900).
```

## mops code help

Show the Code Mode extension reference.

```text
Usage: mops code help [-h] [--json] [PATH]
Show the Code Mode extension reference from the selected project daemon.
Parameters:
      [PATH]
Options:
  -h, --help                Show this help message and exit.
      --json                Emit the catalog as JSON.
```
