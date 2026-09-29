# Agent instructions

## Project state

This is a prototype:

- Make breaking changes as necessary, do not maintain backward compatibility unless told otherwise.
- Prefer simple code over defensive over-engineering, unless told otherwise.

## Agent skills

### Issue tracker

Issues are tracked as local Markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses a single-context domain documentation layout. See `docs/agents/domain.md`.

Verified MPS API documentation lives in
[`specificlanguages/mps-api-research`](https://github.com/specificlanguages/mps-api-research).

### Coding conventions and guidelines

- [Kotlin](docs/agents/kotlin-coding-guidelines.md)
- [Gradle](docs/agents/gradle-conventions.md)

### MPS agent workflows

Reusable MPS agent guidance lives in the sibling [mps-agent-guidance](../mps-agent-guidance/README.md) repository. When
reproducing bugs in MPS projects, follow its [reproducer guidance](../mps-agent-guidance/docs/bug-reproduction.md),
including version selection before preparing a checkout or runtime. Its
[review skill](../mps-agent-guidance/skills/mops-review/SKILL.md) covers MPS model review.
