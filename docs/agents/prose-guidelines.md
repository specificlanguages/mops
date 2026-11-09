# Prose Guidelines

Rules for prose in this repo: Markdown docs, command descriptions and help text, `mops explain` topics, the `examples/`
pages, and the `skills/mops/` skill. Agents read all of it, so these rules are about predictable agent behavior as much
as human readability. For a skill or `AGENTS.md`, also use the `writing-for-agents` skill.

## One source of truth

Each fact lives in one authoritative place. Changing a behavior then needs one prose edit, or none.

- **Point to what you can look up.** Code, `--help` output, generated pages, schemas, and config are sources of truth.
  Prose that copies them is a cache: it goes stale on the next change, and nothing tells the writer to update it.
- **Name only what the reader acts on.** When prose describes a record, an option set, or a set of values, name the 2–3
  items that the reader needs to decide something, then point to the full list. Example: the `check` summary record
  needs `total`, `truncated`, and `hint` in prose, not every field. A list that grows each time the code grows is the
  smell.
- **When no lookup exists yet, keep the full list in exactly one place** and point to it from the others.
- **Edit the source, not a generated copy.** `docs/reference/` and `skills/mops/references/` are generated (see
  [Command reference](../development.md#command-reference) and
  [Bundled task recipe checks](../development.md#bundled-task-recipe-checks)).

## Relevance

- When you change a behavior, update or delete every line it made stale, in the same pull request. Stale lines that stay
  become sediment that readers must dig through.
- Delete a sentence that does not change what the reader does. Delete the whole sentence, not some of its words.
- Keep a concept's definition, rules, and caveats under one heading, so that reading one part brings the rest.

## Wording

- State the behavior you want. A prohibition makes the forbidden behavior more likely for an agent. Use one only for a
  hard guardrail, and pair it with the positive rule.
- Use the terms from [`GLOSSARY.md`](../../GLOSSARY.md), with the same meaning everywhere. When the same idea appears in
  several places, use the same word for it, not synonyms.
- A pointer to other material (a line in `AGENTS.md`, a "See `mops explain …`") says what the material is and when to
  read it.
