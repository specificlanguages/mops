# mops daemon

Generated from `mops daemon --help` by `./gradlew :cli:updateReference`. Do not edit this page. Global options are
listed in the [index](index.md).

Inspect or control mops daemon processes.

```text
Usage: mops daemon [-h] [COMMAND]
Inspect or control mops daemon processes.
Commands:
  ping    Start or reuse a project daemon and exchange a ping request.
  status  Print daemon status.
  stop    Stop a daemon process.
  help    Display help information about the specified command.
Options:
  -h, --help                Show this help message and exit.
```

## mops daemon ping

Start or reuse a project daemon and exchange a ping request.

```text
Usage: mops daemon ping [-h]
Start or reuse a project daemon and exchange a ping request.
Options:
  -h, --help                Show this help message and exit.
```

## mops daemon status

Print daemon status.

```text
Usage: mops daemon status [-h] [--all]
Print daemon status.
Options:
      --all                 Show daemon state for all projects.
  -h, --help                Show this help message and exit.
```

## mops daemon stop

Stop a daemon process.

```text
Usage: mops daemon stop [-h] [--all]
Stop a daemon process.
Options:
      --all                 Stop daemons for all projects.
  -h, --help                Show this help message and exit.
```
