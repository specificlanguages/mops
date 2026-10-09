# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Layout

This repo uses a single-context domain documentation layout:

- `GLOSSARY.md` at the repo root for project vocabulary and concise domain definitions.
- `docs/adr/` at the repo root for architectural decision records.

## Before exploring, read these

- **`GLOSSARY.md`** at the repo root.
- **`docs/adr/`** - read ADRs that touch the area you're about to work in.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront.
The `domain-modeling` skill creates them lazily when terms or decisions actually get resolved.

## File structure

```text
/
|-- GLOSSARY.md
|-- docs/adr/
|   |-- 0001-example-decision.md
|   `-- 0002-example-decision.md
`-- src/
```

## Use the glossary's vocabulary

Domain concepts use title case in internal domain documents so their use as defined terms is visible. User-facing
messages and documentation use ordinary lowercase prose for these terms.

When your output names a domain concept, such as in an issue title, refactor proposal, hypothesis, or test name, use the
term as defined in `GLOSSARY.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't
use, or there's a real gap to resolve with `domain-modeling`.

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> Contradicts ADR-0007 (event-sourced orders), but worth reopening because...
