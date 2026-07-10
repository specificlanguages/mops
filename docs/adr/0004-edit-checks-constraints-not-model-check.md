# Edit operations check constraints, not a full model check

An **Edit Operation** evaluates **Constraints** as part of applying, never a full **Model Check** (typesystem and checking rules), which is a separate operation because it may be costly. The `--constraints` mode chooses what a **Constraint Violation** does. `advisory` evaluates and reports violations but applies and saves the batch anyway. `best-effort` (the default) blocks the batch on any violation, and additionally warns about concepts whose language did not load and so could not be checked. `strict` also blocks on violations but treats a concept that could not be checked as a failure rather than a warning. Every mode reports the violations.

## Considered Options

- **Always run a full Model Check on edit** — rejected: too costly for a per-edit operation, and it would block legitimate intermediate states that are only well-formed once a sequence of edits completes.
- **Apply edits raw, never check** — rejected: matches the existing `resave` no-check behavior but lets an agent silently produce structurally broken models.
- **Check Constraints, block by default, allow an advisory override, always report** (chosen) — cheap enough to run per edit, refuses broken edits by default, but lets a caller apply anyway while still surfacing what was violated.

## Consequences

Constraint evaluation must be cheap. The batch mutates the affected loaded models in memory and then evaluates Constraints over the resulting state; when a blocking mode (`best-effort` or `strict`) rejects the violations, the affected models are reloaded from source, discarding the in-memory changes. This best-effort rollback leaves the loaded model as it was before the batch. Reporting violations is independent of whether they block, so an `advisory` edit still tells the caller what it broke.
