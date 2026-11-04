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
Start or reuse a project daemon, exchange a ping request over a loopback
socket, and print the structured response. The project is --project-root, or
else the nearest directory at or above the working directory that contains a .
mps directory.
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

Without --all, the command infers the current project from the working
directory; with --all, it reads every known daemon record. It sends an
authenticated ping with a two-second connection and read timeout and reports
`running` or `unreachable`. It does not start daemons or remove unreachable
records. A daemon can be unreachable because it is stopped, unresponsive, or
unable to authenticate the request; pings remain responsive during normal long
operations. When no record matches, it prints the metadata line `# no mops
daemons` and exits 0.
```

## mops daemon stop

Stop a daemon process.

```text
Usage: mops daemon stop [-h] [--all]
Stop a daemon process.
Options:
      --all                 Stop daemons for all projects.
  -h, --help                Show this help message and exit.

Sends a stop request to each selected daemon and waits for its process to exit,
force-killing it if necessary. It prints one action report line per record. A
stopped daemon's record is removed and reported as `stopped ...`. If the stop
request gets no response, the record is removed as stale and reported as
`removed stale daemon record ...; process not signalled`; the recorded process
is not killed. If the daemon acknowledges the stop but its process survives the
force-kill, the record is kept so the next `daemon stop` retries it, the PID is
reported on stderr, and the command exits non-zero. When no record matches, it
prints the metadata line `# no mops daemons` and exits 0.
```
