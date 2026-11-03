---
name: field-root-causes
description: Root cause analysis of a field test, benchmark, or transcript in which an agent used mops.
disable-model-invocation: true
---

# Root causes from a field test

Turn evidence of an agent using mops into a list of **problems** that someone can fix. A problem is one root cause, with
every symptom it caused. The **workaround** is the signal: what the agent did instead of the obvious mops path shows the
problem more clearly than the error it hit.

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

### 2. List the symptoms

Count the failed calls and retries in the evidence. For a self-reported log, count its entries and say the count is
self-reported.

Walk the evidence in time order. Note each **symptom**: a failed call, a retry, a workaround, a manual step, a wrong or
misleading result, an unexplained gap in time, a nudge from a human or coordinator. For each, record the evidence
pointer (file and line or timestamp) and its **cost**: the minutes or extra calls it cost the agent, where the evidence
shows it.

Put a failure the task expected, such as a check of a fixture built to be invalid, in a separate list of **expected
failures** with its evidence pointer.

Done when the failed calls and retries among the symptoms and the expected failures add up to your count.

### 3. Find the root cause of each symptom

For each symptom:

1. Write down the workaround, if any.
2. Ask _why_ until the answer names a change that would have altered what the agent did at the moment of the mistake. A
   message in the output reaches the agent at that moment; a document reaches it only if the evidence shows the agent
   read it there.
3. Assign one **kind** (see below).
4. Record **how we know**: _reproduced_ on current `main`, _seen in code_ on current `main` (give `path:line`), or
   _inferred_. A reproduction recorded in an existing issue counts as reproduced when no change since the test touches
   that code. Reproduce when a project under `test-projects/` already has the languages and models the repro needs;
   otherwise record what you read in the code.
5. If a change since the test already fixed the cause, mark the symptom _fixed since the test_ with the commit.

Done when every symptom has a kind, a "how we know", and a root cause.

### 4. Group into problems and choose fixes

Group symptoms that share a root cause into one problem. The problem takes the kind of the shared root cause, even when
its symptoms first looked like different kinds.

**Generality test** (VISION triage heuristic 1): restate each problem's root cause without scenario words (the domain,
the file format, the test's own names). When a problem fails the test, look for a general cause it shares with other
problems and fold it into that problem. A problem that still fails is _scenario-specific_.

Choose one fix per problem from the **fix levels** (see below): the cheapest level that would have changed what the
agent did in the evidence. Go higher only when the evidence shows a cheaper level already existed and the agent still
failed. Say so in the cause. Also name **where** the fix is made: mops, MPS, a tool in the MPS ecosystem, or the test's
setup.

These problems get no fix, with the reason: scenario-specific problems, problems fixed since the test, problems whose
cause is unknown, and problems where no change could help.

Search the issue tracker for each problem by domain concept, not by symptom wording (VISION triage heuristic 3). Link an
existing issue instead of proposing a duplicate.

Done when every problem has passed or failed the generality test, and has one fix or a reason for none, a place for each
fix, and an existing issue or none. A problem's cost is the sum of its symptoms' costs.

### 5. Report

The user reads the report and decides for each problem whether to file it as an issue. The user has not read this skill,
so write the report in plain words. Write the report as a local draft outside the repository, in the problem format
below. Order the problems that have a fix by cost: a cost in minutes first, highest first, then a cost only in calls,
highest first, then no cost shown. Then add short lists, in this order: setup problems, problems fixed since the test,
problems with an unknown cause, scenario-specific problems. When the test compared mops with another tool, add that
tool's section (see below).

Present the report to the user. The user picks problems to file and dismisses the rest. Publish the report, file the
picked problems, and close the report, as described under _Field-test reports_ in `docs/agents/issue-tracker.md`.

Done when every field and term in the report is in the problem format below or is plain English, the user has seen every
problem, and either the report is closed with every picked problem filed or the user declined to publish.

### 6. Improve the format

Ask the user which fields or terms in the report were unclear, too long, or not used. Fix an unclear term by renaming it
in the problem format and everywhere in this skill, so the report stays free of an explanation section.

Done when the user has answered, and each change they asked for is made in this skill, or the user said nothing needs to
change.

## Kinds

- **bug**: mops does the wrong thing.
- **unclear**: mops does the right thing but does not say what the agent needed to know: a success that hides an error,
  a warning that is missing, an error that arrives late or does not name the fix.
- **missing**: mops cannot do it, or only in a way so awkward that the agent took a workaround.
- **upstream**: MPS or another tool in the MPS ecosystem behaves this way. When mops could also surface it, make a
  second problem with the same symptoms, kind _unclear_, where mops.
- **setup**: the test's prompt, task wording, environment, or supervision caused it.
- **agent knowledge**: the agent lacked knowledge that no mops command, MPS check, or MPS-ecosystem tool could have
  given it at the moment of the mistake.
- **unknown**: the evidence does not show the cause. Record what evidence would show it.

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

List the other tool's symptoms with step 2. In the report, add a section for the other tool with two lists:

- **Strengths** that remove a root cause found in the mops evidence. Name the problem each one affects; it becomes a
  candidate fix for that problem.
- **Its own symptoms** with their causes and costs, so the comparison stays fair. They become mops problems only when
  mops has the same cause.

Done when every mops problem has been checked against the other tool's evidence for a strength, and every failed call
and retry of the other tool is in its symptoms or among its expected failures.

## Problem format

```markdown
### <root cause in general words>

- Kind: <kind> · How we know: <reproduced on main | seen in code: path:line | inferred> · <fixed since the test: commit>
- What happened: <each symptom with evidence pointer>
- Cost to the agent: <minutes and extra calls, or: not shown>
- What the agent did instead: <the workaround, or: nothing>
- Cause: <symptom → … → root cause>
- Fix: <the change> (<fix level>) · Where: <mops | MPS | MPS-ecosystem tool name | setup> (or: No fix: <reason>)
- Existing issue: <link | none>
```
