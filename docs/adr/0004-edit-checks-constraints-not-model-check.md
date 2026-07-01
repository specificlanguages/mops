# Edit operations check constraints, not a full model check

An **Edit Operation** evaluates **Constraints** before applying and, by default, refuses to modify the model on any **Constraint Violation**. A force flag applies the edit anyway. Either way, mops reports the violations. A full **Model Check** (typesystem and checking rules) is a separate operation because it may be costly, and is not run as part of an edit.

## Considered Options

- **Always run a full Model Check on edit** — rejected: too costly for a per-edit operation, and it would block legitimate intermediate states that are only well-formed once a sequence of edits completes.
- **Apply edits raw, never check** — rejected: matches the existing `resave` no-check behavior but lets an agent silently produce structurally broken models.
- **Check Constraints, block by default, allow force, always report** (chosen) — cheap enough to run per edit, refuses broken edits by default, but lets a caller override while still surfacing what was violated.

## Consequences

Constraint evaluation must be cheap and must run before the model is modified so a blocked edit leaves the loaded model untouched. Reporting violations is independent of whether they block, so a forced edit still tells the caller what it broke.
