# Edit Scripts embed GraalJS behind a curated facade

> **Status: PLANNED — NOT IMPLEMENTED.** This ADR records a design decision that has not been built. There is no
> `mops model eval` command, no embedded GraalJS engine, no `.d.ts` facade, and no script timeout in the source; the
> `model` command group registers only `get-node` and `edit`. The design below stands as the intended
> direction, not a description of current behavior.

An **Edit Script** (`mops model eval`) is JavaScript executed by the daemon on an embedded GraalJS engine with host
access denied: the script sees only a curated facade of **Node Handles**, entry globals, and the existing **Edit
Operations** as methods — never raw MPS objects. The whole script runs as one write action over the same
write-executor as the JSON batch, with end-state **Constraint** evaluation (ADR-0004) and all-or-nothing rollback.
JavaScript was chosen because agents compose it fluently without learning a bespoke notation, and GraalJS because it
embeds as plain jars and can confine the script to exactly the facade.

## Considered Options

- **Keep growing the JSON batch notation** — rejected: every capability needs a new JSON shape plus explain topics
  and drift guards, and the notation can never express computed edits (read, branch, loop) without becoming a
  programming language anyway.
- **Compiled Java or Kotlin snippets** — rejected: requires a compile pipeline, cannot be sandboxed (compiled code
  reaches the whole MPS classpath), and iterates slowly for snippet-sized edits.
- **Expose the raw MPS API (`SNode`) to scripts** — rejected even though it looks more "guessable": agents guess MPS
  APIs unreliably (it is niche in training data), the real API takes meta-objects rather than names so mirroring it
  faithfully is unusable from scripts, and raw mutation bypasses the identity-preserving guarantees the **Intent
  Operations** exist to provide.
- **Script-controlled commits** — rejected: staged commits break the "a failure changes nothing" contract both edit
  front-ends are built on.

## Consequences

Every script mutation passes through the same resolution, guards, and **Constraint** machinery as the batch, so
there is one edit engine with two notations. String names (with diagnosis) rather than typed meta-objects are the
API's currency. Scripts run while holding the write lock, so a wall-clock timeout with uncatchable abort and
rollback is part of the contract. The published `.d.ts` is the notation's contract and is held truthful by a drift
guard; its one-screenful budget is the alarm for API bloat. The MPS distribution ships no Graal artifacts and
GraalJS relocates its ICU4J, so the embedding needs no shading; Truffle runs interpreted on the daemon JVM, which is
adequate for script-sized work.
