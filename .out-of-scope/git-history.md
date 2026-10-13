# Git history

mops does not compare or render models across Git revisions. It operates on the project that is open in the daemon,
never on a revision's bytes or on a loose `.mps` file outside that project.

## Why this is out of scope

Comparing models across commits belongs to git tooling; at most mops could one day offer a renderer that such tooling
calls. `docs/VISION.md` keeps mops on the open project, never on revisions.

Every interface proposed for the request (a semantic diff of two revisions, rendering a model at a commit, rendering a
file from the index) needs a model that is not part of the open project to be loaded into a repository where its
languages resolve, and then rendered through the EDT-bound headless editor. That is L-size infrastructure, and the open
questions are not small:

- Which language versions should render an old revision when the project's languages have moved on since?
- What happens with languages that are not built at that revision, or whose runtime differs from the loaded one?
- File-per-root models span several files; a single revision's file is not a model.
- A `git textconv` filter would depend on a running daemon with the right project open.

A cheap partial, `mops render model` for the loaded project, does not address the cross-revision need and is not worth
doing alone.

## What could return

A plain renderer that `git textconv` calls (model bytes in, rendered text out), with no diffing or revision awareness in
mops itself. Only if a later field test asks for it.

## Prior requests

- #49: "Semantic diffs across Git revisions"
