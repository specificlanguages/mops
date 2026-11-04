---
name: transcript-analysis
description:
  Analyze a transcript, log, benchmark, or papercut list of an agent using mops, and report incidents, causes, and
  fixes.
disable-model-invocation: true
---

# Transcript analysis

The goal is to make agents more effective with mops. Mine the **evidence** of an agent's session for **incidents**, find
their **causes**, and propose **fixes**. The maintainer reads the report and decides which fixes become issues. Read
`docs/VISION.md` first: it says what mops is for and what is out of scope.

- **Evidence**: transcripts, operation logs, results files, harness counters, captured output, papercut lists.
- **Incident**: anything that slowed the agent or sent it the wrong way: a failed call, a retry, a misleading result, a
  workaround, a manual step, a long gap, a nudge from a human. A failure the task expected, such as checking a fixture
  built to be invalid, is not an incident.
- **Cause**: what a fix would remove. One cause often explains several incidents, and one incident can have several
  causes.
- **Fix**: a change that removes a cause.

## What matters here

**Watch what the agent did, not what it said.** The workaround is the clearest signal: what the agent did instead of the
obvious mops path shows the cause better than the error it hit. A self-reported note (a papercut, the agent's own
summary) is a lead to check against captured output or the current code. Say which incidents rest only on a self-report.

**Check against current `main`.** Find the mops commit the agent used. If the evidence does not name it, derive it and
say how. Look at `git log <that commit>..main`. A cause that a later commit removed is _fixed since the test_. Reproduce
a cause when a project under `test-projects/` makes it cheap; otherwise point to the code (`path:line`). Say for each
cause whether it is reproduced, seen in code, or inferred.

**Stop at a cause someone can change.** Ask why until you reach something a change would have fixed at the moment of the
mistake: an error message the agent saw, a result it read, a default it used. A document counts only if the evidence
shows the agent read it then. The places a fix can go:

- mops (code, output, errors, the shipped skill, docs);
- MPS or another tool in the MPS ecosystem, which we can influence;
- the MPS agent skills (`mps-idioms`, `mps-review`), for MPS knowledge that mops should not re-teach;
- the test's setup (prompt, task, environment, supervision).

Compilers, shells, and other general tools are fixed context. When the agent simply made a mistake that no change could
have prevented, say so instead of proposing a mops fix.

**Prefer general, cheap fixes.** State each cause without the test's domain words. If it only makes sense for this
scenario, fold it into a more general cause or mark it scenario-specific. Choose the cheapest fix that would have
changed what the agent did, from cheapest to strongest:

1. document it;
2. report it in a later command (`check`, `diagnose`);
3. report it in the result of the same call;
4. fail early, with a message that names the fix;
5. make the mistake impossible.

Choose a stronger fix when the evidence shows that a cheaper one already existed and the agent still went wrong. A
feature of another MPS-ecosystem tool can also be the fix.

**Compare fairly.** When the test compares mops with another tool, note where the other tool avoided a mops cause (a
candidate fix), and list the other tool's own incidents too.

**Avoid duplicates.** Search the issues by domain concept, not by error wording, and link an existing issue instead of
proposing a new one.

## Report

Write a draft outside the repository, with one section per cause:

```markdown
### <the cause, in general words>

- Incidents: <each incident with an evidence pointer; mark self-reported ones>
- Cost to the agent: <minutes and extra calls, summed over the incidents, or: not shown>
- What the agent did instead: <the workaround, or: nothing>
- How we know: <reproduced on main | seen in code: path:line | inferred> <· fixed since the test: commit>
- Fix: <the change> (<fix level>) · Where: <mops | MPS | tool name | MPS agent skills | setup>, or: No fix: <reason>
- Existing issue: <link | none>
```

Put the causes with a fix first, most costly first. Then add short lists: setup causes, causes fixed since the test,
causes not found (with the evidence that would show them), scenario-specific causes, and agent mistakes.

Show the draft to the maintainer, who picks the fixes that become issues. Then publish the report and open the issues as
described under _Field-test reports_ in `docs/agents/issue-tracker.md`.
