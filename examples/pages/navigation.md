# Navigate and read

Navigation uses separate space-delimited segments. Model names include stereotypes. A `.suffix` segment is relative to
the preceding module and is only the part after the module name: `.main` after `sample.solution` means
`sample.solution.main`. A model named exactly like its module has no suffix form; give its full name as the second
segment, or use its reference. Use references to disambiguate names; a lone `sample.solution` is ambiguous when a module
and a model share that name. `list` follows containment, not reference links.

{{table:commands|Command}}

`list --depth` ranges from 0 to 8; the default is 1. `list` shows at most 50 children per level; `--limit 0` lifts the
cap, which matters when listing project modules. `--summary` cannot be combined with `--depth`. `get node --ancestry`
adds the containment chain from the node up to its root. Rendering is a reading aid, not a serialization that can be
edited and imported back; the default editor can leave information out, so inspect the subtree with `list --depth` or
`get node` when a rendered view is not enough.
