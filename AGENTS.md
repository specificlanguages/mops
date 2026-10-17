# Agent instructions

## Project state

`docs/VISION.md` states what mops is for, who it serves, its principles, and what is out of scope. Read it before
triaging requests or designing features.

This is a prototype:

- Make breaking changes as necessary, do not maintain backward compatibility unless told otherwise.
- Prefer simple code over defensive over-engineering, unless told otherwise.

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
- [Gradle](docs/agents/gradle-conventions.md)
- [Commits and pull requests](docs/development.md#commits-and-pull-requests): Conventional Commits for commit messages
  and pull request titles
