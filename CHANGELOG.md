# Changelog

## 0.3.0 (Unreleased)

- Added `mops find node-by-id <id> [in <scope>]` for the pasted-from-grep flow: given a bare **Node ID** in either
  spelling, it reports every node with that id — one per model, since a Node ID is unique only within its model — each
  as a standard node summary row with its full **Node Reference**. Zero matches is a successful empty result; a
  malformed id fails with a parse error. `in <scope>`, `--limit`, `--json`, and `--refs-only` behave as in the other
  find subcommands. See `mops explain node-ref` and `mops explain scope`.
- Made both **Node ID** spellings — the decimal form mops prints and the encoded form persisted in `.mps` files —
  resolve wherever a node id appears, including the id part of a serialized **Node Reference**. A reference copied
  straight from a `.mps` file now resolves the same as one from mops output, in `model get-node`, `find usages`, `list`,
  scope clauses, and `model edit` targets. A malformed id fails with a parse error rather than a misleading "not found".
- Made `mops list` and every `mops find` subcommand print short concept names by default in text output, with
  `--full-concept` to restore the fully qualified names. Short names are safe as the default because they round-trip
  through the shared concept-name resolver. JSON output keeps qualified concept names regardless of the flag.
- Added `--refs-only` to `mops find instances`, `find usages`, and `find root-by-name`: it prints one serialized Node
  Reference per line and nothing else, so results pipe straight into `mops model get-node` and other commands. It
  combines with `in <scope>` and the other filters, is mutually exclusive with `--json`, and reports any truncation on
  stderr so stdout stays a clean stream of references.
- Gave `mops list` three output-shaping options for wide targets. `--limit N` caps each level's children (default 50,
  `0` unlimited) and appends a `truncated <shown> <total>` row to any level it clips, so the omission is explicit rather
  than silent. `--summary` prints grouped counts instead of enumerating children — per **Role** with the dominant
  concepts for a node, per concept for a model's roots, per model for a module, and per kind for the project or
  repository — and is rejected together with `--depth`. `--role <role>` lists only a node target's children in one
  containment role, and errors on a module, model, project, or repository target. JSON output carries the same summary
  and truncation information structurally (`summary`, `childTotal`).
- Added the `mops model edit` **`wrap`** and **`unwrap`** operations. `wrap` (`{"op": "wrap", "target", "concept",
  "role", ...}`) puts a fresh node of `concept` in the target's exact slot and moves the target under the wrapper's
  `role` (at an optional `position` among inline-built siblings); the wrapper is built like an `addChild` inline spec
  (`properties`/`references`/`children`, Move/Copy Leaves included) and `as` binds it. `unwrap` (`{"op": "unwrap",
  "target", "keep"}`) promotes `keep` — which must resolve to a proper descendant of the target — into the target's
  slot and deletes the rest; `keep` may be any depth, so a multi-level strip is one operation. Both work on Root Nodes
  (the wrapper or kept node becomes a root of the same model), preserve identity (inbound References keep resolving),
  and are Constraint-checked on the end state. Node attributes travel inside the target's subtree and are never copied
  onto a wrapper. See `mops explain edit.wrap` and `mops explain edit.unwrap`.
- Added the `mops model edit` **`replace`** operation: `{"op": "replace", "target": <target>, "with": <inline
  position>, "as": ...}`. The replacement — a fresh-node spec, a **Move Leaf**, or a **Copy Leaf** — takes the target's
  exact slot (same parent, Containment Role, and sibling index, or Root Node position when the target is a root), and
  the remainder of the target's old subtree is deleted. Move Leaves inside `with` may adopt nodes from inside the
  replaced target, keeping their identities (and inbound References) alive; a bare Move Leaf of a descendant is an
  unwrap. The swap uses MPS's own `SNodeUtil.replaceWithAnother`; the end state is Constraint-checked like any other
  edit. Inbound References to deleted nodes are left dangling (visible to `mops model check`, not rewritten). See
  `mops explain edit.replace`.
- Extended the `mops model edit` inline-subtree notation so a position in a `children` array holds a fresh-node spec, a
  **Move Leaf** (`{"role": ..., "move": <target>}`) that adopts an existing node with its subtree identity-preservingly,
  or a **Copy Leaf** (`{"role": ..., "copy": <target>}`) that deep-copies it with fresh ids. Leaves work at any depth in
  `addChild` and `addRoot`, mix with fresh specs, and are checked against Constraints like any other placement. Inline
  references now accept the canonical `{"role": ..., "to": <target>}` form (the same grammar `setReference` uses,
  aliases included) alongside the get-node-shaped `{"role": ..., "target": {...}}` form that lets `get-node` output
  round-trip. Mixing a fresh spec with a leaf, setting both `move` and `copy`, or setting both `to` and `target` is
  rejected with a field-naming decode error. See `mops explain inline-subtree`.
- Added a trailing `in <scope-segments>` Search Scope clause to `mops find instances` and `mops find usages`, using the same navigation-target grammar as `mops list`. A scope resolves to the repository (`in /`), a module, a model, or a node subtree, and is searched exhaustively (including read-only library and stub models within it); without a clause the search stays scoped to editable project sources. See `mops explain scope`.
- **Breaking:** Removed `--all` from `mops find instances` and `mops find usages`; `in /` now searches the whole MPS repository instead.
- Added `--named <pattern>` and `--role <role>` filters to `mops find instances`. `--named` keeps only instances whose Node Name matches the given Go-to-Node pattern (the same matcher as `mops find root-by-name`; see `mops explain name-pattern`); `--role` keeps only instances filling that containment role, so Root Nodes never match. Both filters, and the scope clause, AND together to narrow results.
- Made `find instances` and `model edit` explain a `CONCEPT_NOT_FOUND` instead of reporting a bare "not found": they
  distinguish a malformed name, an unknown owning language, a present-but-unloaded language (reporting that language's
  load diagnosis and pointing at `diagnose module`), and a loaded language that lacks the concept (suggesting similarly
  named concepts). They also forgive a dropped `.structure.` infix, resolving `<language>.<ConceptName>` when the
  language is loaded.
- Made the daemon load the MPS distribution's bundled plugins (from `<mps-home>/plugins`) at startup, so stock languages
  that depend on plugin modules (editor tooltips, console, execution, debugger, ...) register their runtimes. Without
  them a large share of the project's languages stayed unloaded and their concepts were invisible to name lookup, so
  `find instances` reported `CONCEPT_NOT_FOUND` for concepts that were present and compiled.
- Added `mops diagnose modules` and `mops diagnose module <ref>`, which report why the project's languages and
  Java-bearing modules did or did not load. Each unloaded module is classified — absent, no Java facet, classes disabled,
  not built, blocked by broken dependencies (reported recursively down to the root modules to fix), or a residual
  runtime load failure. `diagnose modules` lists all languages and facet-bearing project modules with flattened root
  causes; `diagnose module <ref>` inspects any single module (including ones absent or without a facet) and prints the
  full dependency problem tree. This traces a `find instances` `CONCEPT_NOT_FOUND` to its cause, since a concept resolves
  by name only when its owning language's runtime is loaded.
- Made `mops model get-node` report a node's containment context: the exported node carries a `parent` object for its immediate containing node (containment role, `root`/`node` type, name, concept, and reference), and `--ancestry` nests that `parent` recursively up to the root node. `find usages` and `find instances` now carry each result's immediate parent too, in both JSON (a nested `parent` summary) and text (trailing `parent` columns) for non-root results.
- Added `mops find root-by-name <pattern>`, which finds root nodes by name using MPS's Go-to-Node pattern matching (camel-hump and `*` wildcards, case-insensitive, matches anywhere in the name), ranked best match first. Searches editable project sources by default; append an `in <scope-segments>` clause to search a module, a model, or the whole repository (`in /`) exhaustively. Because it searches Root Nodes only, a node or root-node subtree scope is rejected with a pointer to `find instances --named`. See `mops explain name-pattern` and `mops explain scope`.
- Added `mops model edit --constraints=advisory|best-effort|strict` (default `best-effort`). `best-effort` blocks on constraint violations and warns (once per language) about constraints it could not check because a language was not loaded; `strict` fails on such a case; `advisory` evaluates, reports, and applies anyway.
- Made reads report a node whose MPS concept could not be resolved (usually an uncompiled language) with `conceptValid: false` instead of failing, and marked an unresolvable `get-node` reference target with `resolved: false`.
- Made daemon startup reject an empty or module-less project (no `.mps/modules.xml`, or zero project modules) so every request surfaces the startup error rather than returning nothing.
- Enriched `get-node` reference targets with the target's name and concept when the target resolves.
- Pivoted the prototype to Kotlin application subprojects for `cli` and `daemon`.
- Added a persistent per-project daemon lifecycle behind `mops --mps-home <path> daemon ping`.
- Added `mops daemon status` and `mops daemon stop` for inspecting and stopping known project daemons.
- Made daemon startup prepare isolated MPS/IDEA runtime directories and report environment readiness plus daemon log path.
- Removed the old Go/offline command surface.
- Removed the old Live IDE bridge subproject and decision records.

## 0.2.0 - 2026-04-29

- Added `mops generate-ids`, which generates unused regular node IDs for standalone `.mps` files or file-per-root model folders, defaulting to short Java-friendly base64 output with a `--long` decimal mode.
- Added `mops list-models`, which discovers `.mps` files and file-per-root `.model` metadata files and emits a model-ID-to-location JSON map.
- Renamed the CLI entry point from `mps-decompress` to `mops decompress`.
- Changed XML output to use `<empty />` syntax for empty elements to improve readability and reduce token usage.
- Deferred `mops help <command>` support as a useful future CLI usability improvement.
