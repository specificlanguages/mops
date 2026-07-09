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
mops --mps-home <path> model get-node [--ancestry] <node-reference>
mops --mps-home <path> model get-node [--ancestry] <model-target> <node-id>
```

Exports one resolved node as a JSON tree through the daemon, addressed by a serialized node reference or by a model
target plus node id. An unresolved target fails with `NODE_NOT_FOUND`. The exported object carries the node's concept,
id, properties, references, and child subtree, plus a `parent` object describing its immediate containing node: the
containment `role` by which the node sits in it, a `type` of `root` or `node`, and the parent's name, concept, and
reference. A Root Node has no `parent`. `--ancestry` nests `parent` recursively up to the Root Node instead of carrying
only the immediate parent.

```sh
mops --mps-home <path> find instances [--exact] [--limit N] [--json] <concept>
```

Searches **Editable Project Sources** for nodes that are instances of a fully qualified MPS concept
(`<language>.structure.<ConceptName>`), including subconcepts and MPS interface matches by default. `--exact` restricts
results to nodes whose direct concept is the queried one. An existing concept with no matches succeeds with no rows.

A concept that does not resolve fails with `CONCEPT_NOT_FOUND`, and the error explains which of the causes applies: the
name is not well formed; the owning language is unknown to the project; the owning language is present but not loaded
(in which case it reports that language's load diagnosis and points at `diagnose module`, since only loaded languages
contribute concepts to name lookup); or the language is loaded but has no such concept (in which case it suggests
similarly named concepts from that language). A dropped `.structure.` infix is forgiven: `<language>.<ConceptName>`
resolves as if written in full when the language is loaded.

Text output is tab-separated rows of `root` or `node`, the node name (or `<unnamed>`), the node's actual concept,
and its serialized node reference; a non-root node appends its immediate parent as trailing `parent`, parent name (or
`<unnamed>`), parent concept, and parent reference columns. `--json` prints an object with `limit`, `truncated`, and a
`nodes` array whose non-root entries carry a nested `parent` summary.

```sh
mops --mps-home <path> find usages [--limit N] [--json] <node-reference>
mops --mps-home <path> find usages [--limit N] [--json] <model-target> <node-id>
```

Searches **Editable Project Sources** for references to one resolved target node, addressed the same way as
`model get-node`. An unresolved target fails with `NODE_NOT_FOUND`. Text output is tab-separated rows of `usage`, the
reference role, and the owning node's name (or `<unnamed>`), concept, and reference; the owner is typed `root` or `node`
by its position in its model. A non-root owner appends its immediate parent as trailing `parent`, parent name (or
`<unnamed>`), parent concept, and parent reference columns. `--json` prints an object with `limit`, `truncated`, and a
`usages` array whose entries carry the reference `role` and a nested `owner` node summary, itself carrying a nested
`parent` summary for a non-root owner. Both `find` modes default to `--limit 100`, treat
`--limit 0` as unlimited, reject negative limits, and append a `truncated` row (in text) or set `truncated` (in JSON)
only when more matches exist than were returned.

```sh
mops --mps-home <path> diagnose modules [--all] [--json]
```

Reports the load state of the project's languages and every other project module that has a Java facet, so a
`CONCEPT_NOT_FOUND` from `find instances` can be traced to its cause: a concept resolves by name only if its owning
language's runtime is loaded. Text output starts with a `modules\t<loaded>/<total> loaded\t<failed> failed` summary,
then one tab-separated row per failed module — its name, `kind`, and a reason code — followed, when the reason is
`BROKEN_DEPENDENCIES`, by indented rows naming the root-cause modules to fix. It ends with a `note` line: modules
without a Java facet are not listed and can be inspected individually with `diagnose module`. `--all` also lists the
modules that loaded; `--json` prints the full structured diagnosis.

```sh
mops --mps-home <path> diagnose module [--json] <module>
```

Diagnoses one module, addressed by module name or serialized module reference, including modules not shown by
`diagnose modules` (those without a Java facet, or absent from the repository). Text output is a header row (`module`,
`kind`, `present=<bool>`, `loaded=<bool>`) followed by the module's load-problem tree, indented by depth so a
dependency chain reads from the module down to its root causes; `--json` prints the structured diagnosis.

Both commands classify an unloaded module with a reason code: `ABSENT` (not in the repository), `NOT_A_MODULE` (resolves
to something that does not load classes), `NO_JAVA_FACET` (no Java facet — a defect for a language, informational for
another module), `CLASSES_DISABLED` (the Java facet is configured not to load classes), `NOT_BUILT` (classes not
generated/compiled yet), `BROKEN_DEPENDENCIES` (blocked by depended-on modules, reported recursively), and
`RUNTIME_LOAD_FAILED` (everything present but the runtime still did not register — usually a class-link or version error
in the daemon log).

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
