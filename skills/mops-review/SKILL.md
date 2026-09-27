---
name: mops-review
description:
  Review JetBrains MPS model changes using mops to navigate and render models and inspect references. Use when reviewing
  MPS projects with mops available.
---

# Review MPS models with mops

## Prepare the runtime

Build the target project's languages **before running mops against it**. `mops wrapper` discovers runtime paths but does
not build languages. Production builds may omit test languages; use `diagnose module` and targeted
`make module <test-language>` when a required test language is `NOT_BUILT`.

Generated wrappers invoke `mops` through PATH. When using a development distribution, put its `bin` directory first on
PATH; snapshot version strings may not distinguish different builds.

## Inspect models

Default searches cover editable project sources and exclude models MPS marks read-only, even when their files are local.
For missing results, search in an explicit model scope or `in /` for the whole repository.

`render node` shows the default editor presentation, which can omit nested queries. Use
`list <root-reference> --depth=N` or `get node` to obtain the omitted child's reference, then render that child
directly.

`find usages` follows exact reference targets; searching for a class root need not find references to its constructors.

## Identify review locations

Line numbers in `.mps` persistence files are useless for identifying review locations. Use full node references or node
URLs. For compactness, identify the model once and use node IDs within that model. `find node-by-id` resolves persisted
node IDs to full references.
