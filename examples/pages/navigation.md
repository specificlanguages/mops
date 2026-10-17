# Navigate and read

Navigation uses separate space-delimited segments. Model names include stereotypes; a `.suffix` segment is relative to
the preceding module, and a bare `.` segment names the model whose name equals the module name (`sample.app .` is the
model `sample.app` of module `sample.app`). Use references to disambiguate names. `list` follows containment, not
reference links.

{{table:commands|Command}}

`list --depth` ranges from 0 to 8; the default is 1. Each level shows up to 50 children and ends in an indented
`# truncated: ...` metadata line when more exist; `--limit 0` lifts the cap. `--summary` cannot be combined with
`--depth`. `get node` prints the whole subtree unless `--depth N` or `--summary` bounds it; a `truncatedAt` field marks
a bounded export, and stubs below the depth carry a `reference` to fetch next. Pass `--compact` for single-line JSON.
Rendering is a reading aid, not a serialization that can be edited and imported back.
