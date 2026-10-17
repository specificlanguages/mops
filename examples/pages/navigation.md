# Navigate and read

Navigation uses separate space-delimited segments. Model names include stereotypes; a `.suffix` segment is relative to
the preceding module. Use references to disambiguate names. `list` follows containment, not reference links.

{{table:commands|Command}}

`list --depth` ranges from 0 to 8; the default is 1. Each level shows up to 50 children and ends in an indented
`# truncated: ...` metadata line when more exist; `--limit 0` lifts the cap. `--summary` cannot be combined with
`--depth`. Rendering is a reading aid, not a serialization that can be edited and imported back.
