---
name: mops-daemon
description:
  Start, inspect, restart, and recover mops project daemons, and coordinate concurrent reads and exclusive operations.
  Use when running mops commands, diagnosing a stuck daemon, or cleaning up daemon state.
---

# Working with mops daemons

## Coordinate commands

Independent `list`, `get node`, `find`, `diagnose`, and `code help` commands for the same project can run concurrently.
Consecutive queued readers share one refresh of external changes and may execute together. A later reader may wait for
the next batch; parallel requests do not all necessarily start at once.

Run edits, creation, builds, tests, model checks, rendering, and `code run` sequentially for the same project, including
across agents sharing it. These operations have exclusive project admission. `code run` remains exclusive even when its
program only reads. Reader batches and exclusive operations do not overlap, and a queued exclusive operation prevents
later readers overtaking it.

Batch related queries into one Code Mode program when they depend on each other or need one read action. Never call a
daemon-backed mops command for the same project from inside that program: it would wait for the program's exclusive
admission to finish. Use the Code Mode API within the program instead; locate its guidance with `mops skill path code`.

Ping and stop are handled independently of project admission, so a long operation does not block them. Stop acknowledges
shutdown, stops accepting connections, and waits for accepted requests to finish. The CLI can force termination if the
process does not exit within its stop deadline.

Commands for separate projects have separate daemons and can run concurrently. Offline commands such as `mops --help`,
`mops explain`, and `mops skill path` do not use the daemon.

## Inspect and restart

Use the project's `mopsw` wrapper when available. Otherwise use `mops` with the MPS and Java homes needed for startup.
Keep the same `--daemon-home` throughout inspection, stopping, and startup if using a custom daemon location.

```sh
mops --project-root /path/to/project daemon status
mops --project-root /path/to/project daemon stop
mops --project-root /path/to/project daemon ping
```

Run these commands one at a time. `status` probes an existing record without starting a daemon and prints its PID and
workspace. `stop` stops the recorded daemon; `ping` starts or reuses one. There is no separate restart command. Restart
before switching the project's daemon to a different MPS or Java home.

`mops daemon status --all` discovers recorded daemons across projects without requiring a current project. Use
project-scoped `stop` for recovery; `stop --all` interrupts every recorded project daemon.

A status of `unreachable` indicates that the status probe failed within its two-second timeout; it does not prove that
the process has exited. Pings remain responsive during normal long operations. Inspect the log and process state before
assuming that a daemon is dead. Avoid stopping a healthy daemon during a build, test run, or edit unless you intend to
interrupt that work.

## Recover a stuck daemon

1. Stop launching requests for the project. Capture the PID and workspace from `daemon status` or the workspace's
   `daemon.json` before calling `stop`. Inspect `WORKSPACE/logs/daemon.log` and the original command's error.
2. Try project-scoped `daemon stop`. An acknowledged stop waits for process exit and force-kills it if necessary. If the
   stop request gets no response, the command removes the record without killing the process. The message
   `removed stale daemon record` does not prove that the process has exited.
3. If the recorded PID is still alive, inspect its command line and confirm that it is the mops daemon for this
   workspace before terminating it. On macOS/Linux, use `ps -p PID -o pid=,command=`, then `kill PID`; use
   `kill -KILL PID` only if it remains stuck. Check that the process has exited before starting a replacement. Avoid
   killing all Java or MPS processes.
4. Once the old process is gone, remove its stale `WORKSPACE/daemon.json` if it remains. If startup still reports
   corrupt IDEA state, move aside only this workspace's `daemon/config` and `daemon/system` directories while its daemon
   is stopped. Preserve logs and test reports for diagnosis.
5. Run project-scoped `daemon ping` once with the intended MPS and Java homes. Recheck the model state before retrying a
   failed mutation: commands are non-transactional and a disconnected client does not establish whether an edit
   completed or was saved.

The default daemon home is `$XDG_CACHE_HOME/mops/daemon`, or `~/.cache/mops/daemon` when that variable is unset.
Per-project workspaces are hashed directories under `projects/`; use the reported workspace rather than assuming a hash.
Deleting a live daemon's record or cache does not stop it.

Initial startup allows 300 seconds. For a project that needs longer, set `MOPS_DAEMON_STARTUP_TIMEOUT_SECONDS` to a
positive whole number. This changes startup waiting, not execution deadlines. A Code Mode timeout terminates the project
daemon; ordinary query timeouts do not cancel the active request.
