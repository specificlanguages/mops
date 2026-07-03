# mops

`mops` is a small helper CLI for helping LLMs work with JetBrains MPS models.
This checkout is a Gradle-rooted Kotlin prototype with two application subprojects: `cli/` and `daemon/`.

## Usage

```sh
mops --help
mops --mps-home /path/to/mps daemon ping
mops --mps-home /path/to/mps model resave path/to/model
mops --mps-home /path/to/mps model edit --file edit-batch.json
mops daemon status
mops daemon stop
```

The CLI starts or reuses a per-project daemon process for most commands.

## Commands

```sh
mops --mps-home <path> daemon ping
```

Starts or reuses the persistent daemon for the current MPS project, exchanges one ping request over a loopback socket,
and prints the structured response. The command walks upward from the current directory until it finds a `.mps`
directory.

```sh
mops --mps-home <path> model resave <model-target>
```

Resaves one model target through the daemon-backed MPS APIs. The command infers the MPS project, starts or reuses a
per-project daemon, and asks that daemon to persist the target model.

```sh
mops --mps-home <path> model edit [--file PATH]
```

Applies a JSON batch of edit operations through the daemon. Run `mops explain edit` for the operation reference.

```sh
mops --mps-home <path> find instances [--exact] [--limit N] [--json] <concept>
```

Searches **Editable Project Sources** for nodes that are instances of a fully qualified MPS concept, including
subconcepts and MPS interface matches by default. `--exact` restricts results to nodes whose direct concept is the
queried one. An unresolved concept fails with `CONCEPT_NOT_FOUND`; an existing concept with no matches succeeds with no
rows. Text output is tab-separated rows of `root` or `node`, the node name (or `<unnamed>`), the node's actual concept,
and its serialized node reference. `--json` prints an object with `limit`, `truncated`, and a `nodes` array.

```sh
mops --mps-home <path> find usages [--limit N] [--json] <node-reference>
mops --mps-home <path> find usages [--limit N] [--json] <model-target> <node-id>
```

Searches **Editable Project Sources** for references to one resolved target node, addressed the same way as
`model get-node`. An unresolved target fails with `NODE_NOT_FOUND`. Text output is tab-separated rows of `usage`, the
reference role, and the owning node's name (or `<unnamed>`), concept, and reference; the owner is typed `root` or `node`
by its position in its model. `--json` prints an object with `limit`, `truncated`, and a `usages` array whose entries
carry the reference `role` and a nested `owner` node summary. Both `find` modes default to `--limit 100`, treat
`--limit 0` as unlimited, reject negative limits, and append a `truncated` row (in text) or set `truncated` (in JSON)
only when more matches exist than were returned.

```sh
mops daemon status [--all]
mops daemon stop [--all]
```

Inspect or stop known per-project daemon processes. Without `--all`, the command infers the current project from the
working directory. With `--all`, it reads every known daemon record.

## Daemon State

Daemon records, logs, working files, and isolated IDEA config and system directories live outside the MPS project. By
default the CLI stores them under `~/.mops/daemon`; pass `--daemon-home <path>` to use another directory. Each project
gets a stable hashed subdirectory under `projects/`, including:

- `daemon.json` - atomic daemon record with port, token, PID, protocol version, daemon version, project path, MPS home,
  log path, and startup time
- `logs/daemon.log` - daemon startup and runtime log for the current prototype
- `daemon/idea-config` and `daemon/idea-system` - isolated IDEA directories passed to the daemon JVM

Daemon commands use loopback socket IPC with a per-daemon token. Requests are serialized by the daemon. Stale daemon
records are removed when the recorded process or socket is no longer reachable.

## Build And Test

```sh
./gradlew check
./gradlew installMops
./gradlew :cli:run --args="--mps-home /path/to/mps daemon ping"
./gradlew :cli:run --args=--help
./gradlew :daemon:run --args=--help
```

The repository is a Gradle-rooted Kotlin prototype with two application subprojects: `cli/` and `daemon/`.
