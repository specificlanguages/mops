# Plan / Execute

How mops divides a change or a planned query into a Planner, a Plan and an Executor. This document is part of the
[Design standards](design-standards.md): authors and reviewers apply its rules in the same way, and Logic, Value and
Adapter mean what that document says.

Plan/Execute applies to two types of operation:

- a change that a user requests to their project or environment (creating MPS Modules, applying an edit batch, modifying
  descriptors, running a migration, committing);
- a planned query: a query where choosing how to run it is policy, for example which index answers a Repository Search
  for Node Usages or Concept Instances.

```text
Planner (Logic) ──produces──▶ Plan (Value) ──consumed by──▶ Executor (Adapter)
```

A Plan is a request turned into an object, as in the **Command** pattern: a list of steps, each described as data. For a
change, a step says what to write. For a query, a step says which source to search, such as an index or a scan of a set
of MPS Models.

**`plan-split`**. Every change and every planned query has a Planner, a Plan and an Executor. The Planner and Executor
are separate classes. Special cases:

- A write that only keeps mops's own records (test reports, caches, temporary folders, lock files) stays inside one
  Adapter, with no Plan.
- A query with only one way to run it has no Plan.
- In an edit batch, the user writes the Plan. The Planner checks and resolves it before the first change.
- Checks that MPS can only run on the changed MPS Model (constraints, cardinality, scopes) run after the Executor,
  inside the same lock. Logic decides from the violations whether to keep the change or roll it back.
- In Code Mode, each primitive that changes an MPS Model is an Executor step with no Planner.

**`plan-complete`**. The Plan is a Value (all Value rules apply). It describes every step completely; the Executor needs
no other input to carry it out. Two exceptions:

- A step that refers to an MPS Node that an earlier step creates is resolved during execution.
- A Plan made and executed inside one MPS lock may hold live MPS objects. Dry-run then renders or returns a Value copy
  of it.

**`executor-follows-plan`**. The Executor is mechanism. The Plan sets _what_ to change or _which_ source to search; the
Executor may choose _how_, for example writing a temporary file and renaming it. If execution fails, it reports which
step failed; it does not substitute a different step.

**`dry-run-plans-only`**. Dry-run of a change is the Planner plus rendering of the Plan, with no Executor. If dry-run
needs special-case code, one of the rules above is broken somewhere.
