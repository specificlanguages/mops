---
name: field-root-causes
description: Root cause analysis of a field test, benchmark, or transcript in which an agent used mops.
disable-model-invocation: true
---

# Root causes from a field test

Turn evidence of an agent using mops into root causes that someone can fix. The **workaround** is the signal: what the
agent did instead of the obvious mops path shows the gap more clearly than the error it hit.

## Steps

### 1. Gather the evidence

List every source: transcripts, operation logs, results files, harness telemetry, captured tool output. Mark each as
_captured_ (written by the harness) or _self-reported_ (written by the agent about itself). List what is not captured,
such as per-call timings or a transcript.

Record the mops commit the agent used, the MPS version, and the platform. When the evidence does not name the mops
commit, derive it (for example from the run's start time and `git log`) and say how. Then list the mops changes since
the test with `git log <test commit>..main`.

Done when every source is listed with its kind, the missing evidence is listed, the versions and platform are recorded
with their origin, and you have the list of changes since the test.

### 2. Inventory the symptoms

Count the failed calls and retries in the evidence. For a self-reported log, count its entries and say the count is
self-reported.

Walk the evidence in time order. Record each **symptom**: a failed call, a retry, a workaround, a manual step, a wrong
or misleading result, an unexplained gap in time, a nudge from a human or coordinator. For each, record the evidence
pointer (file and line or timestamp) and the cost in minutes or calls where the evidence shows it.

Put a failure the task expected, such as a check of a fixture built to be invalid, in a separate list of **expected
failures** with its evidence pointer.

Done when the failed calls and retries among the symptoms and the expected failures add up to your count.

### 3. Find the root cause of each symptom

For each symptom:

1. Write down the workaround, if any.
2. Ask _why_ until the answer names a change that would have altered what the agent did at the moment of the mistake. A
   message in the output reaches the agent at that moment; a document reaches it only if the evidence shows the agent
   read it there.
3. Assign one **layer** (see below).
4. Assign a **confirmation level**: _reproduced_ on current `main`, _seen in code_ on current `main` (give `path:line`),
   or _inferred_. A reproduction recorded in an existing issue counts as reproduced when no change since the test
   touches that code. Reproduce when a project under `test-projects/` already has the languages and models the repro
   needs; otherwise record what you read in the code.
5. If a change since the test already fixed the cause, mark the symptom _fixed since the test_ with the commit.

Done when every symptom has a layer, a confirmation level, and a root cause.

### 4. Group and choose fixes

Group symptoms that share a root cause into one record. The record takes the layer of the shared root cause, even when
its symptoms first looked like different layers.

**Generality test** (VISION triage heuristic 1): restate each record's root cause without scenario words (the domain,
the file format, the test's own names). When a record fails the test, look for a general cause it shares with other
records and fold it into that record. A record that still fails is _scenario-specific_.

Choose one fix per record from the **fix ladder** (see below): the cheapest rung that would have changed what the agent
did in the evidence. Go higher only when the evidence shows a cheaper rung already existed and the agent still failed.
Record that evidence in the why chain. The **home** is where the fix is made: mops, MPS, a tool in the MPS ecosystem, or
the test's setup.

These records get `Fix: none` with the reason: scenario-specific records, records fixed since the test, undetermined
records, and records where no change could help.

Search the issue tracker for each record by domain concept, not by symptom wording (VISION triage heuristic 3). Link an
existing issue instead of proposing a duplicate.

Done when every record has passed or failed the generality test, and has one fix or `Fix: none`, a home for each fix,
and an existing issue or none.

### 5. Report

Write the report as a local draft outside the repository, in the record format below. Order the records with a fix by
cost in the evidence: a cost in minutes first, highest first, then a cost only in calls, highest first, then no cost
shown. Then add short lists, in this order: setup records, records fixed since the test, undetermined records,
scenario-specific records. When the test compared mops with another tool, add that tool's section (see below).

Present the report to the user. The user picks records to file and dismisses the rest. Publish the report, file the
picked records, and close the report, as described under _Field-test reports_ in `docs/agents/issue-tracker.md`.

Done when the user has seen every record, and either the report is closed with every picked record filed, or the user
declined to publish.

## Layers

- **defect**: mops does the wrong thing.
- **silence**: mops does the right thing but does not say what the agent needed to know: a success that hides a problem,
  a warning that is missing, an error that arrives late or does not name the fix.
- **gap**: mops cannot do it, or only in a way so awkward that the agent took a workaround.
- **upstream**: MPS or another tool in the MPS ecosystem behaves this way. When mops could also surface it, make a
  second record with the same symptoms, layer _silence_, and home mops.
- **setup**: the test's prompt, task wording, environment, or supervision caused it.
- **knowledge**: the agent lacked knowledge that no mops command, MPS check, or MPS-ecosystem tool could have given it
  at the moment of the mistake.
- **undetermined**: the evidence does not show the cause. Record what evidence would show it.

## Fix ladder

From cheapest to strongest:

1. Document it, in the mops skill or docs, or, for MPS knowledge that mops would only re-teach (VISION out of scope), in
   the MPS agent skills such as `mps-idioms` and `mps-review`, which live outside this repo.
2. Report it in a later command (`check`, `diagnose`).
3. Report it in the result of the same call.
4. Fail early, with a message that names the fix.
5. Make the mistake impossible (the API does the right thing).

A capability of another tool in the MPS ecosystem, or a new mops capability modelled on it, can also be the fix. Tools
outside the MPS ecosystem, such as compilers and shell utilities, are fixed context.

## When the test compares mops with another tool

Inventory the other tool's evidence with step 2. In the report, add a section for the other tool with two lists:

- **Strengths** that remove a root cause found in the mops evidence. Name the record each one affects; it becomes a
  candidate fix for that record.
- **Its own symptoms** with their causes and costs, so the comparison stays fair. They become mops records only when
  mops has the same cause.

Done when every mops record has been checked against the other tool's evidence for a strength, and every failed call and
retry of the other tool is in its symptoms or among its expected failures.

## Record format

```markdown
### <root cause in general words>

- Layer: <layer> · Confirmation: <reproduced | seen in code: path:line | inferred> · <fixed since the test: commit>
- Symptoms: <each with evidence pointer and cost>
- Workaround: <what the agent did instead>
- Why chain: <symptom → … → root cause>
- Fix: <ladder rung>: <the change> · Home: <mops | MPS | MPS-ecosystem tool name | setup> (or: Fix: none: <reason>)
- Issue: <existing issue link | none>
```
