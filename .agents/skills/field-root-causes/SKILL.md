---
name: field-root-causes
description: Root cause analysis of a field test, benchmark, or transcript in which an agent used mops.
disable-model-invocation: true
---

# Root causes from a field test

Turn evidence of an agent using mops into a list of **faults** that someone can fix. A **failure** is what the evidence
shows: the agent's work went worse than the obvious mops path would have gone. A fault is what causes failures, and what
a fix removes. One fault can cause many failures. The **workaround** is the signal: what the agent did instead of the
obvious mops path shows the fault more clearly than the error it hit.

## Steps

### 1. Gather the evidence

List every source: transcripts, operation logs, results files, harness telemetry, captured tool output. Mark each as
_captured_ (written by the harness) or _self-reported_ (written by the agent about itself). List what is not captured,
such as per-call timings or a transcript.

Record the mops commit the agent used, the MPS version, and the platform. When the evidence does not name the mops
commit, derive it (for example from the run's start time and `git log`) and say how. Then list the mops changes since
the test with `git log <test commit>..main`.

Done when every source is marked captured or self-reported, the missing evidence is listed, the versions and platform
are recorded with their origin, and you have the list of changes since the test.

### 2. List the failures

Count the failed calls and retries in the evidence. For a self-reported log, count its entries and say the count is
self-reported.

Walk the evidence in time order. Note each **failure**: a failed call, a retry, a workaround, a manual step, a wrong or
misleading result, an unexplained gap in time, a nudge from a human or coordinator. For each, record the evidence
pointer (file and line or timestamp) and its **cost**: the minutes or extra calls it cost the agent, where the evidence
shows it.

Put a failure the task expected, such as a check of a fixture built to be invalid, in a separate list of **expected
failures** with its evidence pointer.

Done when the failed calls and retries among the failures and the expected failures add up to your count.

### 3. Find the fault behind each failure

For each failure:

1. Write down the workaround, if any.
2. Ask _why_ until the answer names a change that would have altered what the agent did at the moment of the mistake.
   That answer is the fault. A message in the output reaches the agent at that moment; a document reaches it only if the
   evidence shows the agent read it there.
3. Assign the fault one **kind** (see below).
4. Record **how we know**: _reproduced_ on current `main`, _seen in code_ on current `main` (give `path:line`), or
   _inferred_. A reproduction recorded in an existing issue counts as reproduced when no change since the test touches
   that code. Reproduce when a project under `test-projects/` already has the languages and models the repro needs;
   otherwise record what you read in the code.
5. If a change since the test already removed the fault, mark it _fixed since the test_ with the commit.

Done when every failure has a fault, a kind, and a "how we know".

### 4. Group by fault and choose fixes

Group failures that have the same fault. Each fault keeps one kind, even when its failures first looked like different
kinds.

**Generality test** (VISION triage heuristic 1): restate each fault without scenario words (the domain, the file format,
the test's own names). When a fault does not pass the test, look for a general fault it shares with others and fold it
into that one. A fault that still does not pass is _scenario-specific_.

Choose one fix per fault from the **fix levels** (see below): the cheapest level that would have changed what the agent
did in the evidence. Go higher only when the evidence shows a cheaper level already existed and the agent still failed.
Say so in the cause. Also name **where** the fix is made: mops, MPS, a tool in the MPS ecosystem, or the test's setup.

These faults get no fix, with the reason: scenario-specific faults, faults fixed since the test, faults of kind
_unknown_, and faults where no change could help.

Search the issue tracker for each fault by domain concept, not by failure wording (VISION triage heuristic 3). Link an
existing issue instead of proposing a duplicate.

Done when every fault has passed or failed the generality test, and has one fix or a reason for none, a place for each
fix, and an existing issue or none. A fault's cost is the sum of its failures' costs.

### 5. Report

The user reads the report and decides for each fault whether to file it as an issue. The user has not read this skill,
so write the report in plain words. Write the report as a local draft outside the repository, in the fault format below.
Order the faults that have a fix by cost: a cost in minutes first, highest first, then a cost only in calls, highest
first, then no cost shown. Then add short lists, in this order: setup faults, faults fixed since the test, faults of
kind _unknown_, scenario-specific faults. When the test compared mops with another tool, add that tool's section (see
below).

Present the report to the user. The user picks faults to file and dismisses the rest. Publish the report, file the
picked faults, and close the report, as described under _Field-test reports_ in `docs/agents/issue-tracker.md`.

Done when every field and term in the report is in the fault format below or is plain English, the user has seen every
fault, and either the report is closed with every picked fault filed or the user declined to publish.

### 6. Improve the format

Ask the user which fields or terms in the report were unclear, too long, or not used. Fix an unclear term by renaming it
in the fault format and everywhere in this skill, so the report stays free of an explanation section.

Done when the user has answered, and each change they asked for is made in this skill, or the user said nothing needs to
change.

## Kinds

- **bug**: mops does the wrong thing.
- **unclear**: mops does the right thing but does not say what the agent needed to know: a success that hides an error,
  a warning that is missing, an error that arrives late or does not name the fix.
- **missing**: mops cannot do it, or only in a way so awkward that the agent took a workaround.
- **upstream**: MPS or another tool in the MPS ecosystem behaves this way. When mops could also surface it, add a second
  fault with the same failures, kind _unclear_, where mops.
- **setup**: the test's prompt, task wording, environment, or supervision caused it.
- **agent knowledge**: the agent lacked knowledge that no mops command, MPS check, or MPS-ecosystem tool could have
  given it at the moment of the mistake.
- **unknown**: the evidence does not show the fault. Record what evidence would show it.

## Fix levels

From cheapest to strongest:

- **document**: in the mops skill or docs, or, for MPS knowledge that mops would only re-teach (VISION out of scope), in
  the MPS agent skills such as `mps-idioms` and `mps-review`, which live outside this repo.
- **later check**: report it in a later command (`check`, `diagnose`).
- **in the result**: report it in the result of the same call.
- **fail early**: fail with a message that names the fix.
- **prevent**: make the mistake impossible (the API does the right thing).

A capability of another tool in the MPS ecosystem, or a new mops capability modelled on it, can also be the fix. Tools
outside the MPS ecosystem, such as compilers and shell utilities, are fixed context.

## When the test compares mops with another tool

List the other tool's failures with step 2. In the report, add a section for the other tool with two lists:

- **Strengths** that remove a mops fault. Name the fault each one affects; it becomes a candidate fix for that fault.
- **Its own failures** with their faults and costs, so the comparison stays fair. They become mops faults only when mops
  has the same fault.

Done when every mops fault has been checked against the other tool's evidence for a strength, and every failed call and
retry of the other tool is in its failures or among its expected failures.

## Fault format

```markdown
### <the fault in general words>

- Kind: <kind> · How we know: <reproduced on main | seen in code: path:line | inferred> · <fixed since the test: commit>
- Failures: <each failure with evidence pointer>
- Cost to the agent: <minutes and extra calls, or: not shown>
- What the agent did instead: <the workaround, or: nothing>
- Cause: <failure → … → fault>
- Fix: <the change> (<fix level>) · Where: <mops | MPS | MPS-ecosystem tool name | setup> (or: No fix: <reason>)
- Existing issue: <link | none>
```
