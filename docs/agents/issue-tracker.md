# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues in `specificlanguages/mops`. Use the `gh` CLI for all operations;
it infers the repo from the clone you run it in.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**, with appropriate `--label` and `--state` filters:

  ```sh
  gh issue list --state open --json number,title,body,labels,comments \
    --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'
  ```

- **Make an issue a sub-issue of a parent**: `gh issue create --parent <parent> ...`, or
  `gh issue edit <parent> --add-sub-issue <child>` afterwards.
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

## Specs and features

- **A feature** is a parent issue carrying the PRD in its body. Implementation issues are its sub-issues.
- **An implementation issue** body has the sections Problem, What to build, Explicitly not doing, Acceptance criteria.
  Code pointers are `path:line` and must be verified against the current tree when written.
- **Issue state** is expressed with the triage labels in `docs/agents/triage-labels.md`, plus open/closed. There is no
  "in progress" label: an issue being worked on is open, labelled `ready-for-agent` or `ready-for-human`, and assigned.
- **Comments** record decisions and findings that change the spec. Edit the body when the spec itself changes, and say
  so in a comment.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads
this flag.)_

GitHub shares one number space across issues and PRs, so a bare `#42` may be either: resolve with `gh pr view 42` and
fall back to `gh issue view 42`.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`.

## Archived local specs

Before October 2026, issues and PRDs were local Markdown files under `.scratch/<feature-slug>/` in the main worktree
(gitignored). That content was not migrated. Treat it as read-only history; new specs go to GitHub, and a GitHub issue
that supersedes a local spec should link to the local path in its body so the history stays findable.
