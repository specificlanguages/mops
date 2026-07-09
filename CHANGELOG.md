# Changelog

## 0.3.0 (Unreleased)

- Added a trailing `in <scope-segments>` Search Scope clause to `mops find instances` and `mops find usages`, using the same navigation-target grammar as `mops list`. A scope resolves to the repository (`in /`), a module, a model, or a node subtree, and is searched exhaustively (including read-only library and stub models within it); without a clause the search stays scoped to editable project sources. See `mops explain scope`.
- **Breaking:** Removed `--all` from `mops find instances` and `mops find usages`; `in /` now searches the whole MPS repository instead.
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
