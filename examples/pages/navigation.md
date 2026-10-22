# Navigate and read

Navigation uses separate space-delimited segments. Model names include stereotypes. A `.suffix` segment is relative to
the preceding module and is only the part after the module name: `.main` after `sample.solution` means
`sample.solution.main`. A bare `.` segment names the model whose name equals the module name (`sample.app .` is the
model `sample.app` of module `sample.app`). Use references to disambiguate names; a lone `sample.app` is ambiguous when
a module and a model share that name. `list` follows containment, not reference links.

{{table:commands|Command}}

`list --depth` ranges from 0 to 8; the default is 1. Each level shows up to 50 children and ends in an indented
`# truncated: ...` metadata line when more exist; `--limit 0` lifts the cap, which matters when listing project modules.
`--summary` cannot be combined with `--depth`. `get node --ancestry` adds the containment chain from the node up to its
root. `get node` prints the whole subtree unless `--depth N` or `--summary` bounds it; a `truncatedAt` field marks
a bounded export, and stubs below the depth carry a `reference` to fetch next. Pass `--compact` for single-line JSON.
Rendering is a reading aid, not a serialization that can be edited and imported back; the default editor can
leave information out, so inspect the subtree with `list --depth` or `get node` when a rendered view is not enough.
