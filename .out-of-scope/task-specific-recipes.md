# Task-specific recipes

mops's examples topics and skills document how to use mops, not how to carry out a particular investigation. A recipe
topic built around one task (an `experiments` topic with "fresh JVM", "simulate an external edit", and "probe for a
race" recipes) is out of scope.

## Why this is out of scope

Two reasons:

- **Task-specific recipes.** Examples and skills show mops's own operations and how they compose. The problem space of
  investigations is unbounded: every field test produces a new set of experiments, and a topic per investigation would
  grow without a stopping rule and without any way to tell whether it is complete.
- **Re-teaching MPS or the JVM** (a non-goal in `docs/VISION.md`). Most recipes of this kind are MPS or JVM knowledge
  wearing a mops label: what `SModel.reloadFromSource()` does, when `System.gc()` clears weak references, how iteration
  order changes between JVM runs. That is MPS and JVM documentation.

The durable part of such a request is whatever mops itself guarantees. For the experiments request that was the Code
Mode execution contract (which thread the program runs on, per-thread access blocks, the lifetime of the `mops` context,
what the timeout does). That is a spec, not an examples topic:
`.scratch/code-mode/issues/03-code-mode-execution-contract.md`.

Daemon restart is documented where it belongs, as daemon operation (`skills/mops-daemon/SKILL.md`, "Inspect and
restart"), not as an experiment recipe.

## Prior requests

- #53: "Recipes for fresh JVM and concurrent reads"
