# Agent instructions

## Project state

`docs/VISION.md` states what mops is for, who it serves, its principles, and what is out of scope. Read it before
triaging requests or designing features.

This is a prototype:

- Make breaking changes as necessary, do not maintain backward compatibility unless told otherwise.
- Prefer simple code over defensive over-engineering, unless told otherwise.

**Sibling paths.** When you change how mops reads, resolves, reports, or prints something, the commands, Code Mode
paths, and edit batches that do the same thing are its sibling paths (`docs/VISION.md`, principle 7). Change every
sibling path in the same pull request, through one shared implementation. You are done when each sibling path is changed
or left for later with an issue, as [Commits and pull requests](docs/development.md#commits-and-pull-requests) says.

**Review load.** The maintainer reviews every pull request, so review time sets the pace. Start a pull request only when
fewer than three ready pull requests wait for review (`gh pr list --draft=false`), not counting dependency updates.
Refactorings that touch the same files run one after another: merge one before you start the next.

## Agent skills

### Issue tracker

Issues and specs are GitHub issues in this repo. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles map one-to-one to GitHub labels. See `docs/agents/triage-labels.md`.

### Domain docs

This repo uses a single-context domain documentation layout. See `docs/agents/domain.md`.

Verified MPS API documentation lives in
[`specificlanguages/mps-api-research`](https://github.com/specificlanguages/mps-api-research).

### Coding conventions and guidelines

- [Kotlin](docs/agents/kotlin-coding-guidelines.md)
- [Design standards](docs/agents/design-standards.md): how to divide Kotlin code into classes and packages. Read when
  you add, split or move a class or package, add I/O or policy to a class, or review such a change
- [Gradle](docs/agents/gradle-conventions.md)
- [Prose](docs/agents/prose-guidelines.md): Markdown docs, help text, explain topics, examples, and skills
- [Commits and pull requests](docs/development.md#commits-and-pull-requests): Conventional Commits for pull request
  titles, commit structure, issues for work left for later, and the review before a pull request is ready
