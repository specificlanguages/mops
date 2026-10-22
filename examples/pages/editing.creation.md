# Create modules and models

{{table:commands|Command}}

Solution presets configure facets, not dependencies, imported languages, devkits, or contents. Supported presets:
`not-generated` (default, no facets), `text` (plain-text generation), `java` (Java compiled and loaded by MPS),
`java-tests` (`java` plus the tests facet), and `java-mps-plugin` (Java extending MPS as a plugin). The creation report
and the `--dry-run` plan print the preset applied and the facets it produced; `--json` carries them as `usagePreset` and
`facets`.
